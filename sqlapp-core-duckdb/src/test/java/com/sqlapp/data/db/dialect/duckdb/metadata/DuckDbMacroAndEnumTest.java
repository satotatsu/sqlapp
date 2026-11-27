/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.duckdb.DuckDb;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.FunctionType;

/**
 * Reads DuckDB macros and ENUM types and recreates them from the generated DDL
 * on every supported driver version.
 */
class DuckDbMacroAndEnumTest {

	@Test
	void readsAndRecreatesMacrosAndEnumTypes() throws Exception {
		try (Connection connection = DriverManager.getConnection("jdbc:duckdb:");
				Statement statement = connection.createStatement()) {
			final Dialect dialect = DialectResolver.getInstance().getDialect(connection);
			final boolean overloads = assertInstanceOf(DuckDb.class, dialect).supportsConstraintMetadata();
			statement.execute("CREATE SCHEMA s1");
			statement.execute("CREATE TYPE s1.mood AS ENUM ('sad', 'o''k', 'a,b')");
			statement.execute("COMMENT ON TYPE s1.mood IS 'feeling'");
			statement.execute("CREATE MACRO s1.\"Select\"(\"from\", b) AS \"from\" + b");
			statement.execute("COMMENT ON MACRO s1.\"Select\" IS 'adds'");
			statement.execute("CREATE MACRO s1.answer() AS 42");
			statement.execute("CREATE MACRO s1.t(n) AS TABLE SELECT * FROM range(n)");
			if (overloads) {
				statement.execute("CREATE MACRO s1.over(a) AS a + 1, (a, b) AS a + b");
			}

			final List<Domain> domains = domains(dialect, connection);
			assertEquals(1, domains.size());
			final Domain mood = domains.get(0);
			assertEquals("mood", mood.getName());
			assertEquals("s1", mood.getSchemaName());
			assertEquals(DataType.ENUM, mood.getDataType());
			assertEquals(List.of("sad", "o'k", "a,b"), List.copyOf(mood.getValues()));
			assertEquals("feeling", mood.getRemarks());

			final List<Function> functions = functions(dialect, connection);
			assertEquals(overloads ? 4 : 3, functions.size());
			final Function select = find(functions, "Select");
			assertEquals(FunctionType.Scalar, select.getFunctionType());
			assertEquals("adds", select.getRemarks());
			assertEquals(List.of("from", "b"), select.getArguments().stream().map(a -> a.getName()).toList());
			assertEquals("CREATE MACRO s1.\"Select\"(\"from\", b) AS (\"from\" + b)", definition(select));
			assertEquals("CREATE MACRO s1.answer() AS 42", definition(find(functions, "answer")));
			final Function table = find(functions, "t");
			assertEquals(FunctionType.Table, table.getFunctionType());
			assertTrue(definition(table).startsWith("CREATE MACRO s1.t(n) AS TABLE SELECT"), definition(table));
			if (overloads) {
				final Function over = find(functions, "over");
				assertTrue(over.getArguments().isEmpty());
				assertNull(over.getStatement() == null || over.getStatement().isEmpty() ? null : over.getStatement());
				final String text = definition(over);
				assertTrue(text.contains("(a) AS (a + 1)") && text.contains("(a, b) AS (a + b)"), text);
			}

			final SqlFactoryRegistry registry = dialect.createSqlFactoryRegistry();
			for (final Function function : functions) {
				execute(statement, registry.createSql(function, SqlType.DROP));
			}
			execute(statement, registry.createSql(mood, SqlType.DROP));
			assertTrue(functions(dialect, connection).isEmpty());
			assertTrue(domains(dialect, connection).isEmpty());

			execute(statement, registry.createSql(mood, SqlType.CREATE));
			for (final Function function : functions) {
				execute(statement, registry.createSql(function, SqlType.CREATE));
			}
			final Domain recreated = domains(dialect, connection).get(0);
			assertEquals(List.of("sad", "o'k", "a,b"), List.copyOf(recreated.getValues()));
			assertEquals("feeling", recreated.getRemarks());
			final List<Function> again = functions(dialect, connection);
			assertEquals(functions.size(), again.size());
			for (final Function function : functions) {
				assertEquals(definition(function), definition(find(again, function.getName())));
			}
			assertEquals("adds", find(again, "Select").getRemarks());
			try (ResultSet rs = statement.executeQuery(
					"SELECT s1.\"Select\"(1, 2), s1.answer(), (SELECT count(*) FROM s1.t(3)), 'o''k'::s1.mood")) {
				assertTrue(rs.next());
				assertEquals(3, rs.getInt(1));
				assertEquals(42, rs.getInt(2));
				assertEquals(3, rs.getInt(3));
				assertEquals("o'k", rs.getString(4));
			}
		}
	}

	private static List<Domain> domains(final Dialect dialect, final Connection connection) {
		final var reader = dialect.getCatalogReader().getSchemaReader().getDomainReader();
		reader.setSchemaName("s1");
		return reader.getAllFull(connection);
	}

	private static List<Function> functions(final Dialect dialect, final Connection connection) {
		final var reader = dialect.getCatalogReader().getSchemaReader().getFunctionReader();
		reader.setSchemaName("s1");
		return reader.getAllFull(connection);
	}

	private static Function find(final List<Function> functions, final String name) {
		return functions.stream().filter(f -> f.getName().equals(name)).findFirst().orElseThrow();
	}

	private static String definition(final Function function) {
		return String.join("\n", function.getDefinition());
	}

	private static void execute(final Statement statement, final List<SqlOperation> operations) throws Exception {
		for (final SqlOperation operation : operations) {
			statement.execute(operation.getSqlText());
		}
	}
}
