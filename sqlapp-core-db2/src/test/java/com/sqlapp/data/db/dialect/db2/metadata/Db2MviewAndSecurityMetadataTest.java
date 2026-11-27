/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.db2.Db2RowPermissions;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class Db2MviewAndSecurityMetadataTest {
	private static void emitSameNamedConstraints(ResultSetNextHandler handler) {
		for (String table : List.of("T1", "T2")) {
			try {
				var values = new java.util.HashMap<String, String>();
				values.put("schema_name", "APP");
				values.put("table_name", table);
				values.put("constraint_name", "SAME_NAME");
				values.put("column_name", table + "_ID");
				values.put("TYPE", "U");
				values.put("ENFORCED", "Y");
				values.put("TEXT", table + "_ID > 0");
				values.put("ref_schema_name", "APP");
				values.put("ref_table_name", "PARENT");
				values.put("ref_column_name", "ID");
				handler.handleResultSetNext(Db2MetadataParityTest.row(values));
			} catch (SQLException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	@Test
	void sameConstraintNamesOnDifferentTablesDoNotMerge() {
		var unique = new Db2UniqueConstraintReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				emitSameNamedConstraints(handler);
				return null;
			}
		}.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		var checks = new Db2CheckConstraintReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				emitSameNamedConstraints(handler);
				return null;
			}
		}.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		var foreignKeys = new Db2ForeignKeyConstraintReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				emitSameNamedConstraints(handler);
				return null;
			}
		}.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(2, unique.size());
		assertEquals(2, checks.size());
		assertEquals(2, foreignKeys.size());
		for (var constraint : unique) {
			assertEquals(1, constraint.getColumns().size());
			assertEquals(constraint.getTableName() + "_ID", constraint.getColumns().getFirst().getName());
		}
		for (var constraint : checks)
			assertEquals(constraint.getTableName() + "_ID > 0", constraint.getExpression());
		for (var constraint : foreignKeys) {
			assertEquals(1, constraint.getColumns().size());
			assertEquals(constraint.getTableName() + "_ID", constraint.getColumns().getFirst().getName());
			assertEquals("PARENT", constraint.getRelatedTableName());
		}
	}

	@Test
	void readsMqtDefinitionAndRoutesVersionedColumns() throws Exception {
		var dialect = DialectResolver.getInstance().getDialect("DB2", 12, 1, 2);
		var reader = new Db2MviewReader(dialect);
		String definition = "CREATE TABLE APP.MQ AS (SELECT ID FROM APP.T) DATA INITIALLY DEFERRED REFRESH DEFERRED";
		Mview mqt = reader.createMview(Db2MetadataParityTest.row(Map.of("schema_name", "APP", "table_name", "MQ",
				"TEXT", definition, "REFRESH", "D", "VALID", "Y", "TBSPACE", "DATA", "QUALIFIER", "APP")));
		assertEquals(definition, String.join("\n", mqt.getDefinition()));
		assertEquals("D", mqt.getSpecifics().get("REFRESH"));
		assertEquals("DATA", mqt.getTableSpaceName());
		assertEquals(dialect.getCatalogReader().getSchemaReader().getTableReader().getColumnReader().getClass(),
				reader.newColumnReader().getClass());
		Schema schema = new Schema("APP");
		schema.getMviews().add(mqt);
		Schema restored = SchemaUtils.readXml(new java.io.StringReader(schema.asXml()));
		assertInstanceOf(Mview.class, restored.getMviews().get("MQ"));
		assertEquals(mqt.getDefinition(), restored.getMviews().get("MQ").getDefinition());
	}

	@Test
	void capturesTwentyPermissionsAndActivationStatesWithTwoSelects() {
		AtomicInteger queries = new AtomicInteger();
		var reader = new Db2_1010SchemaReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				assertNotNull(node);
				assertEquals("APP", context.get("schemaName"));
				int query = queries.incrementAndGet();
				for (int i = 0; i < 21; i++) {
					try {
						if (query == 1)
							handler.handleResultSetNext(Db2MetadataParityTest
									.row(Map.of("CONTROLSCHEMA", "APP", "CONTROLNAME", "P" + i, "TABSCHEMA", "APP",
											"TABNAME", "T" + i, "RULETEXT", "ID > 0", "ENABLE", "Y", "VALID", "Y")));
						else
							handler.handleResultSetNext(Db2MetadataParityTest
									.row(Map.of("TABSCHEMA", "APP", "TABNAME", "T" + i, "CONTROL", "B")));
					} catch (SQLException e) {
						throw new IllegalStateException(e);
					}
				}
				return null;
			}
		};
		Schema schema = new Schema("APP");
		for (int i = 0; i < 20; i++)
			schema.getTables().add(new Table("T" + i));
		reader.readAccessControls(null, schema);
		assertEquals(2, queries.get());
		var snapshot = Db2RowPermissions.get(schema);
		assertEquals(20, snapshot.permissions().size());
		assertEquals(20, snapshot.tables().size());
		assertTrue(snapshot.tables().getFirst().rowActive());
		assertTrue(snapshot.tables().getFirst().columnActive());
	}

	@Test
	void constraintsRemainReadableWithDifferentOrMissingBackingIndexes() {
		var reader = new Db2UniqueConstraintReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				try {
					handler.handleResultSetNext(Db2MetadataParityTest.row(Map.of("schema_name", "APP", "table_name",
							"T", "constraint_name", "UK_T", "TYPE", "U", "ENFORCED", "Y", "column_name", "ID",
							"backing_index_name", "IX_DIFFERENT", "backing_index_schema", "OTHER", "COLORDER", "D")));
					handler.handleResultSetNext(Db2MetadataParityTest.row(Map.of("schema_name", "APP", "table_name",
							"T", "constraint_name", "UK_INFO", "TYPE", "U", "ENFORCED", "N", "column_name", "VALUE")));
				} catch (SQLException e) {
					throw new IllegalStateException(e);
				}
				return null;
			}
		};
		List<UniqueConstraint> constraints = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(2, constraints.size());
		var enforced = constraints.stream().filter(c -> c.getName().equals("UK_T")).findFirst().orElseThrow();
		assertEquals("IX_DIFFERENT", enforced.getIndex().getName());
		assertEquals("OTHER", enforced.getIndex().getSpecifics().get("INDEX_SCHEMA"));
		assertTrue(enforced.isEnable());
		var informational = constraints.stream().filter(c -> c.getName().equals("UK_INFO")).findFirst().orElseThrow();
		assertFalse(informational.isEnable());
		assertEquals("VALUE", informational.getColumns().getFirst().getName());
	}
}
