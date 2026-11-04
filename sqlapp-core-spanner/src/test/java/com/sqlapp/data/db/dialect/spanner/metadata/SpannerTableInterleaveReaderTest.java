/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.spanner.DialectHolder;
import com.sqlapp.data.db.dialect.spanner.sql.SpannerCreateTableFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class SpannerTableInterleaveReaderTest {
	private class Reader extends SpannerTableReader {
		final List<Map<String, String>> rows = new ArrayList<>();
		int queries;

		Reader() {
			super(DialectHolder.defaultDialect);
		}

		@Override
		protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
			queries++;
			try {
				for (var values : rows)
					h.handleResultSetNext(new ExResultSet(null) {
						@Override
						public String getString(String name) {
							return values.get(name);
						}

						@Override
						public Object getObject(String name) {
							return values.get(name);
						}
					});
			} catch (java.sql.SQLException exception) {
				throw new IllegalStateException(exception);
			}
			return null;
		}
	}

	@Test
	void readsFortyLegacyAndModernInterleavedTablesAndRecreatesXmlWithParentFirst() throws Exception {
		final var reader = new Reader();
		for (String schema : List.of("a", "b"))
			for (int i = 0; i < 20; i++) {
				final var row = new HashMap<>(Map.of("table_catalog", "", "table_schema", schema, "table_name",
						"Child" + i, "parent_table_name", "Parent"));
				if (i % 3 == 2)
					row.put("interleave_type", "IN");
				else {
					row.put("on_delete_action", i % 3 == 0 ? "CASCADE" : "NO ACTION");
					if (i % 3 == 1)
						row.put("interleave_type", "IN PARENT");
				}
				reader.rows.add(row);
			}
		final var tables = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(40, tables.size());
		assertEquals(1, reader.queries);
		for (var child : tables) {
			final var schema = new Schema(child.getSchemaName());
			schema.setDialect(DialectHolder.defaultDialect);
			final var parent = new Table("Parent");
			for (var table : List.of(child, parent)) {
				table.getColumns().add(new Column("id").setDataType(DataType.BIGINT).setNotNull(true));
				table.getConstraints().addPrimaryKeyConstraint("pk_" + table.getName(), table.getColumns().get(0));
				schema.getTables().add(table);
			}
			child.getSpecifics().put(SpannerCreateTableFactory.LOCALITY_GROUP, "hot");
			final Schema restored = SchemaUtils.readXml(new java.io.StringReader(schema.asXml()));
			final var restoredChild = restored.getTables().get(child.getName());
			final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			final com.sqlapp.data.db.sql.SqlFactory<Table> factory = registry.getSqlFactory(restoredChild,
					SqlType.CREATE);
			final var operations = factory.createSql(List.of(restoredChild, restored.getTables().get("Parent")));
			assertEquals(2, operations.size());
			assertTrue(operations.get(0).getSqlText().contains("`" + schema.getName() + "`.`Parent`"));
			final String sql = operations.get(1).getSqlText();
			final boolean inOnly = "IN".equals(child.getSpecifics().get(SpannerCreateTableFactory.INTERLEAVE_TYPE));
			assertTrue(
					sql.contains(
							", INTERLEAVE " + (inOnly ? "IN" : "IN PARENT") + " `" + schema.getName() + "`.`Parent`"),
					sql);
			assertEquals(!inOnly, sql.contains("ON DELETE"));
			if (!inOnly)
				assertTrue(
						sql.contains(
								"ON DELETE " + child.getSpecifics().get(SpannerCreateTableFactory.ON_DELETE_ACTION)),
						sql);
			assertTrue(sql.contains(", OPTIONS"), sql);
		}
	}

	@Test
	void queryRetainsOldCatalogShapeWithoutNamingNewPhysicalColumnsAndBindsFilters() {
		final var context = new ParametersContext();
		context.put("schemaName", List.of("app' OR TRUE"));
		context.put("tableName", List.of("Child"));
		final var parameters = new SqlParameterCollection();
		new Reader().getSqlNode(null).eval(context, parameters);
		assertTrue(parameters.getSql().contains("t.*"));
		assertFalse(parameters.getSql().contains("t.INTERLEAVE_TYPE"));
		assertFalse(parameters.getSql().contains("app' OR TRUE"));
		assertEquals(2, parameters.getParameterSize());
	}

	@Test
	void refusesMissingDeleteMetadataForAnEnforcedParentRelationship() {
		final var reader = new Reader();
		reader.rows.add(Map.of("table_name", "Child", "parent_table_name", "Parent", "interleave_type", "IN PARENT"));
		assertThrows(IllegalArgumentException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
	}
}
