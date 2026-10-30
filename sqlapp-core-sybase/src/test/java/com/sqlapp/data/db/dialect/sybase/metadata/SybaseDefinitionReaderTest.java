/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sybase.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.sybase.DialectHolder;
import com.sqlapp.data.db.metadata.ReaderOptions;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class SybaseDefinitionReaderTest {
	private ExResultSet row(String owner, String text) {
		return new ExResultSet(null) {
			@Override
			public int getInt(String name) {
				return 0;
			}

			@Override
			public String getString(String name) {
				return switch (name.toLowerCase(Locale.ROOT)) {
				case "specific_catalog", "table_catalog" -> "db";
				case "specific_schema", "table_schema" -> owner;
				case "routine_name", "specific_name", "table_name" -> "shared";
				case "routine_definition", "view_definition" -> text;
				default -> null;
				};
			}

			@Override
			public java.sql.Timestamp getTimestamp(String name) {
				return null;
			}
		};
	}

	private void feed(ResultSetNextHandler handler, List<ExResultSet> rows) {
		try {
			for (var row : rows)
				handler.handleResultSetNext(row);
		} catch (java.sql.SQLException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	void keepsProcedureOwnersSeparateAndPreservesChunkWhitespace() {
		final var rows = List.of(row("a", "CREATE PROCEDURE a.shared AS SELECT 'a "), row("a", "b'"),
				row("b", "CREATE PROCEDURE b.shared AS SELECT 'other'"));
		final var calls = new int[1];
		final var reader = new SybaseProcedureReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				calls[0]++;
				feed(h, rows);
				return null;
			}
		};
		reader.setReaderOptions(new ReaderOptions());
		final var result = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(1, calls[0]);
		assertEquals(2, result.size());
		assertEquals("a", result.get(0).getSchemaName());
		assertEquals("b", result.get(1).getSchemaName());
		assertEquals("CREATE PROCEDURE a.shared AS SELECT 'a b'", String.join("\n", result.get(0).getDefinition()));
	}

	@Test
	void readsViewsWithoutInformationSchemaOnlyColumnsAndHonorsTextOptions() {
		final var rows = new ArrayList<>(List.of(row("a", "CREATE VIEW a.shared AS SELECT 'a "),
				row("a", "b' AS value"), row("b", "CREATE VIEW b.shared AS SELECT 2 AS value")));
		final var calls = new int[1];
		final var reader = new SybaseViewReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				calls[0]++;
				feed(h, rows);
				return null;
			}
		};
		final var options = new ReaderOptions();
		options.setReadDefinition(true);
		options.setReadStatement(false);
		reader.setReaderOptions(options);
		final var result = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(1, calls[0]);
		assertEquals(2, result.size());
		assertEquals("CREATE VIEW a.shared AS SELECT 'a b' AS value", String.join("\n", result.get(0).getDefinition()));
		rows.clear();
		rows.add(row("a", null));
		assertThrows(IllegalStateException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
		options.setReadDefinition(false);
		assertTrue(reader.doGetAll(null, new ParametersContext(), null).get(0).getDefinition().isEmpty());
	}

	@Test
	void keepsFunctionOwnersSeparateAndRequiresReadableTextOnlyWhenRequested() {
		final var rows = new ArrayList<>(
				List.of(row("a", "CREATE FUNCTION a.shared() RETURNS VARCHAR(20) AS BEGIN RETURN 'a "),
						row("a", "b' END"), row("b", "CREATE FUNCTION b.shared() RETURNS INT AS BEGIN RETURN 2 END")));
		final var reader = new SybaseFunctionReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				feed(h, rows);
				return null;
			}
		};
		final var options = new ReaderOptions();
		reader.setReaderOptions(options);
		final var result = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(2, result.size());
		assertTrue(String.join("\n", result.get(0).getDefinition()).contains("'a b'"));
		rows.clear();
		rows.add(row("a", null));
		assertThrows(IllegalStateException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
		options.setReadDefinition(false);
		options.setReadStatement(false);
		assertTrue(reader.doGetAll(null, new ParametersContext(), null).get(0).getDefinition().isEmpty());
	}
}
