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

class SpannerForeignKeyReaderTest {
	private class Reader extends SpannerForeignKeyConstraintReader {
		final List<Map<String, Object>> rows = new ArrayList<>();
		int queries;

		Reader() {
			super(DialectHolder.defaultDialect);
		}

		@Override
		protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
			queries++;
			try {
				for (var values : rows) {
					h.handleResultSetNext(new ExResultSet(null) {
						private Object last;

						@Override
						public String getString(String name) {
							return Objects.toString(values.get(name), null);
						}

						@Override
						public Object getObject(String name) {
							return values.get(name);
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
				}
			} catch (java.sql.SQLException exception) {
				throw new IllegalStateException(exception);
			}
			return null;
		}

		SqlNode query() {
			return getSqlSqlNode(null);
		}
	}

	private Map<String, Object> row(String schema, int number, long ordinal) {
		final var row = new HashMap<String, Object>();
		row.put("constraint_catalog", "");
		row.put("constraint_schema", schema);
		row.put("constraint_name", "fk" + number);
		row.put("table_name", "Child" + number);
		row.put("column_name", ordinal == 1 ? "z" : "a");
		row.put("ordinal_position", ordinal);
		row.put("referenced_table_catalog", "");
		row.put("referenced_table_schema", schema);
		row.put("referenced_table_name", "Parents");
		row.put("referenced_column_name", ordinal == 1 ? "p2" : "p1");
		row.put("match_option", "SIMPLE");
		row.put("update_rule", "NO ACTION");
		row.put("delete_rule", number % 2 == 0 ? "NO ACTION" : "CASCADE");
		row.put("enforced", number % 2 == 0 ? "NO" : "YES");
		row.put("spanner_state", "COMMITTED");
		return row;
	}

	@Test
	void readsFortyCompositeKeysInOneQueryAndRecreatesThroughXml() throws Exception {
		final var reader = new Reader();
		for (String schema : List.of("a", "b"))
			for (int i = 0; i < 20; i++) {
				reader.rows.add(row(schema, i, 2));
				reader.rows.add(row(schema, i, 1));
			}
		final var keys = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(1, reader.queries);
		assertEquals(40, keys.size());
		for (var key : keys) {
			assertEquals("z", key.getColumns().get(0).getName());
			assertEquals("a", key.getColumns().get(1).getName());
			assertEquals("p2", key.getRelatedColumns().get(0).getName());
			assertEquals("p1", key.getRelatedColumns().get(1).getName());
			assertEquals("COMMITTED", key.getSpecifics().get("spanner_state"));
			final var schema = new Schema(key.getSchemaName());
			schema.setDialect(DialectHolder.defaultDialect);
			final var parent = new Table("Parents");
			parent.getColumns().add(new Column("p1").setDataType(DataType.BIGINT));
			parent.getColumns().add(new Column("p2").setDataType(DataType.BIGINT));
			schema.getTables().add(parent);
			final var child = new Table(key.getTableName());
			child.getColumns().add(new Column("z").setDataType(DataType.BIGINT));
			child.getColumns().add(new Column("a").setDataType(DataType.BIGINT));
			schema.getTables().add(child);
			child.getConstraints().add(key);
			final Schema restored = SchemaUtils.readXml(new java.io.StringReader(schema.asXml()));
			final var restoredKey = (ForeignKeyConstraint) restored.getTables().get(child.getName()).getConstraints()
					.get(0);
			final String sql = DialectHolder.defaultDialect.createSqlFactoryRegistry()
					.createSql(restoredKey, SqlType.CREATE).get(0).getSqlText();
			assertTrue(sql.contains("( `z`, `a` )"), sql);
			assertTrue(sql.contains("( `p2`, `p1` )"), sql);
			assertEquals(key.isEnable(), restoredKey.isEnable());
			assertEquals(!key.isEnable(), sql.endsWith("NOT ENFORCED"));
		}
	}

	@Test
	void rejectsMissingDuplicateAndGappedPositionsAndMixedReferences() {
		for (int mode = 0; mode < 5; mode++) {
			final var reader = new Reader();
			reader.rows.add(row("app", 0, 1));
			final var second = row("app", 0, 2);
			switch (mode) {
			case 0 -> second.put("ordinal_position", null);
			case 1 -> second.put("ordinal_position", 1L);
			case 2 -> second.put("ordinal_position", 3L);
			case 3 -> second.put("referenced_table_name", "Other");
			case 4 -> second.put("enforced", "YES");
			}
			reader.rows.add(second);
			assertThrows(IllegalArgumentException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
		}
	}

	@Test
	void rejectsUnknownEnforcementAndRulesInsteadOfInventingGuarantees() {
		for (String[] invalid : new String[][] { { "enforced", "unknown" }, { "enforced", null },
				{ "delete_rule", "SET DEFAULT" }, { "update_rule", "CASCADE" }, { "match_option", "FULL" } }) {
			final var reader = new Reader();
			final var row = row("app", 0, 1);
			row.put(invalid[0], invalid[1]);
			reader.rows.add(row);
			final Class<? extends RuntimeException> expected = invalid[0].equals("enforced")
					? IllegalArgumentException.class
					: UnsupportedOperationException.class;
			assertThrows(expected, () -> reader.doGetAll(null, new ParametersContext(), null));
		}
	}

	@Test
	void templateUsesExistingOrdinalColumnAndBoundFilters() {
		final var reader = new Reader();
		final var context = new ParametersContext();
		context.put("schemaName", List.of("app' OR TRUE"));
		context.put("tableName", List.of("Child0"));
		context.put("constraintName", List.of("fk0"));
		final var parameters = new SqlParameterCollection();
		reader.query().eval(context, parameters);
		assertTrue(parameters.getSql().contains("fk.ORDINAL_POSITION AS `ordinal_position`"));
		assertFalse(parameters.getSql().contains("app' OR TRUE"));
		assertEquals(3, parameters.getParameterSize());
	}
}
