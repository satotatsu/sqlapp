/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.spanner.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class SpannerPrimaryKeyReaderTest {
	private class Reader extends SpannerUniqueConstraintReader {
		final List<Map<String, Object>> rows = new ArrayList<>();
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
						private Object last;

						@Override
						public String getString(String name) {
							return Objects.toString(values.get(name), null);
						}

						@Override
						public long getLong(String name) {
							last = values.get(name);
							return last == null ? 0 : ((Number) last).longValue();
						}

						@Override
						public boolean wasNull() {
							return last == null;
						}
					});
			} catch (java.sql.SQLException exception) {
				throw new IllegalStateException(exception);
			}
			return null;
		}
	}

	private Map<String, Object> row(String schema, String table, long ordinal) {
		return new HashMap<>(Map.of("table_catalog", "", "table_schema", schema, "table_name", table, "constraint_name",
				"PRIMARY_KEY", "column_name", ordinal == 1 ? "z" : "a", "column_ordering",
				ordinal == 1 ? "ASC" : "DESC", "ordinal_position", ordinal));
	}

	@Test
	void readsFortyCompositeKeysInOneQueryAndReordersBeforeXmlAndDdl() throws Exception {
		final var reader = new Reader();
		for (String schema : List.of("a", "b"))
			for (int i = 0; i < 20; i++) {
				reader.rows.add(row(schema, "Orders" + i, 2));
				reader.rows.add(row(schema, "Orders" + i, 1));
			}
		final var keys = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(40, keys.size());
		assertEquals(1, reader.queries);
		for (var key : keys) {
			assertTrue(key.isPrimaryKey());
			assertEquals(List.of("z", "a"), key.getColumns().stream().map(ReferenceColumn::getName).toList());
			final var table = new Table(key.getTableName()).setSchemaName(key.getSchemaName());
			table.getColumns().add(new Column("z").setDataType(DataType.BIGINT).setNullable(false));
			table.getColumns().add(new Column("a").setDataType(DataType.BIGINT).setNullable(false));
			table.getConstraints().add(key);
			final Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			assertEquals(Order.Desc,
					restored.getConstraints().getPrimaryKeyConstraint().getColumns().get(1).getOrder());
			final String sql = DialectHolder.defaultDialect.createSqlFactoryRegistry()
					.createSql(restored, SqlType.CREATE).get(0).getSqlText();
			assertTrue(sql.contains("PRIMARY KEY"), sql);
			assertTrue(sql.contains("`z` ASC, `a` DESC") || sql.contains("`z`, `a` DESC"), sql);
		}
	}

	@Test
	void rejectsInvalidPositionsNamesOrderingAndRepeatedColumns() {
		for (int mode = 0; mode < 10; mode++) {
			final var reader = new Reader();
			reader.rows.add(row("app", "Orders", 1));
			final var second = row("app", "Orders", 2);
			switch (mode) {
			case 0 -> second.remove("ordinal_position");
			case 1 -> second.put("ordinal_position", 0L);
			case 2 -> second.put("ordinal_position", 1L);
			case 3 -> second.put("ordinal_position", 3L);
			case 4 -> second.put("column_name", "z");
			case 5 -> second.put("column_name", " ");
			case 6 -> second.remove("table_name");
			case 7 -> second.remove("constraint_name");
			case 8 -> second.remove("column_ordering");
			case 9 -> second.put("column_ordering", "unknown");
			}
			reader.rows.add(second);
			assertThrows(IllegalArgumentException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
		}
	}

	@Test
	void queryKeepsPrimaryKeyScopeAndBindsFilters() {
		final var context = new ParametersContext();
		context.put("schemaName", List.of("app' OR TRUE"));
		context.put("tableName", List.of("Orders"));
		context.put("constraintName", List.of("PRIMARY_KEY"));
		final var parameters = new SqlParameterCollection();
		new Reader().getSqlNode(null).eval(context, parameters);
		assertTrue(parameters.getSql().contains("cc.ORDINAL_POSITION AS `ordinal_position`"));
		assertTrue(parameters.getSql().contains("c.INDEX_TYPE IN ('PRIMARY_KEY')"));
		assertFalse(parameters.getSql().contains("app' OR TRUE"));
		assertEquals(3, parameters.getParameterSize());
	}
}
