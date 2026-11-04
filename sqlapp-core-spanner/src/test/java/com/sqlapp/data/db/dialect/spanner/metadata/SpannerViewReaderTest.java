/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.spanner.DialectHolder;
import com.sqlapp.data.db.dialect.spanner.sql.SpannerCreateViewFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class SpannerViewReaderTest {
	private class Reader extends SpannerViewReader {
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
					});
			} catch (java.sql.SQLException exception) {
				throw new IllegalStateException(exception);
			}
			return null;
		}
	}

	private Map<String, String> row(String schema, String name, String security) {
		return new HashMap<>(Map.of("table_catalog", "", "table_schema", schema, "table_name", name, "security_type",
				security, "view_definition", "SELECT id -- retain query boundary\nFROM Orders\nWHERE label <> '𠮷'"));
	}

	@Test
	void readsFortyViewsInOneQueryAndPreservesQueryAndSecurityThroughXmlAndDdl() throws Exception {
		final var reader = new Reader();
		for (String schema : List.of("a", "b"))
			for (int i = 0; i < 20; i++)
				reader.rows.add(row(schema, "view " + i, i % 2 == 0 ? "INVOKER" : "DEFINER"));
		final var views = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(40, views.size());
		assertEquals(1, reader.queries);
		for (var table : views) {
			final var view = (View) table;
			final View restored = SchemaUtils.readXml(new java.io.StringReader(view.asXml()));
			assertEquals(view.getStatement(), restored.getStatement());
			final var security = view.getSpecifics().get(SpannerCreateViewFactory.SECURITY_TYPE);
			assertEquals(security, restored.getSpecifics().get(SpannerCreateViewFactory.SECURITY_TYPE));
			final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			final String sql = registry.createSql(restored, SqlType.CREATE).get(0).getSqlText();
			assertTrue(sql.contains("SQL SECURITY " + security), sql);
			assertTrue(sql.contains("`" + view.getSchemaName() + "`.`" + view.getName() + "`"), sql);
			assertTrue(sql.contains(String.join("\n", view.getStatement())), sql);
		}
	}

	@Test
	void rejectsMissingNameQueryAndSecurityInsteadOfDefaultingMetadata() {
		for (String field : List.of("table_name", "view_definition", "security_type"))
			for (String value : Arrays.asList(null, "", "  ")) {
				final var reader = new Reader();
				final var values = row("app", "v", "INVOKER");
				values.put(field, value);
				reader.rows.add(values);
				assertThrows(IllegalArgumentException.class,
						() -> reader.doGetAll(null, new ParametersContext(), null));
			}
		final var reader = new Reader();
		reader.rows.add(row("app", "v", "unknown"));
		assertThrows(IllegalArgumentException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
	}

	@Test
	void rejectsDuplicateViewIdentitiesAndSupportsEmptyResults() {
		final var reader = new Reader();
		reader.rows.add(row("app", "v", "INVOKER"));
		reader.rows.add(row("app", "v", "DEFINER"));
		assertThrows(IllegalArgumentException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
		final var empty = new Reader();
		assertTrue(empty.doGetAll(null, new ParametersContext(), null).isEmpty());
		assertEquals(1, empty.queries);
	}
}
