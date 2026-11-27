/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.spanner.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class SpannerCheckConstraintReaderTest {
	private class Reader extends SpannerCheckConstraintReader {
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

	private Map<String, String> row(String schema, String name) {
		return new HashMap<>(
				Map.of("table_catalog", "", "table_schema", schema, "table_name", "Orders", "constraint_name", name,
						"check_clause", "quantity IS NOT NULL -- retain the next predicate\r\n\tAND quantity > 0",
						"spanner_state", "COMMITTED"));
	}

	@Test
	void readsManyChecksInOneQueryAndPreservesExpressionThroughXmlAndDdl() throws Exception {
		final var reader = new Reader();
		for (String schema : List.of("a", "b"))
			for (int i = 0; i < 20; i++)
				reader.rows.add(row(schema, "positive " + i));
		final var checks = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(40, checks.size());
		assertEquals(1, reader.queries);
		for (var check : checks) {
			final var table = new Table(check.getTableName()).setSchemaName(check.getSchemaName());
			table.getConstraints().add(check);
			final Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			final var restoredCheck = (CheckConstraint) restored.getConstraints().get(0);
			assertEquals(check.getExpression(), restoredCheck.getExpression());
			assertEquals("COMMITTED", restoredCheck.getSpecifics().get("spanner_state"));
			final var sql = DialectHolder.defaultDialect.createSqlFactoryRegistry()
					.createSql(restoredCheck, SqlType.CREATE).get(0).getSqlText();
			assertTrue(sql.contains(check.getExpression()), sql);
		}
	}

	@Test
	void rejectsIncompleteAndDuplicateMetadata() {
		for (String field : List.of("constraint_name", "table_name", "check_clause")) {
			for (String invalid : Arrays.asList(null, "", "  ")) {
				final var reader = new Reader();
				final var values = row("app", "positive");
				values.put(field, invalid);
				reader.rows.add(values);
				assertThrows(IllegalArgumentException.class,
						() -> reader.doGetAll(null, new ParametersContext(), null));
			}
		}
		final var reader = new Reader();
		reader.rows.add(row("app", "positive"));
		reader.rows.add(row("app", "positive"));
		assertThrows(IllegalArgumentException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
	}

	@Test
	void bindsAllObjectFilters() {
		final var context = new ParametersContext();
		context.put("schemaName", List.of("app' OR TRUE"));
		context.put("tableName", List.of("Orders"));
		context.put("constraintName", List.of("positive"));
		final var parameters = new SqlParameterCollection();
		new Reader().getSqlNode(null).eval(context, parameters);
		assertFalse(parameters.getSql().contains("app' OR TRUE"));
		assertEquals(3, parameters.getParameterSize());
	}
}
