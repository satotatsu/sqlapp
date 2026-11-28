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
import static org.junit.jupiter.api.Assertions.assertThrows;

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
	void completeSchemaRecoversDefaultsOverloadsAndQuotedNamesWithoutCopyingRows() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:duckdb:");
				var statement = connection.createStatement()) {
			final DuckDb dialect = assertInstanceOf(DuckDb.class, DialectResolver.getInstance().getDialect(connection));
			org.junit.jupiter.api.Assumptions
					.assumeTrue(dialect instanceof com.sqlapp.data.db.dialect.duckdb.DuckDbVersions.DuckDb20);
			statement.execute("CREATE SCHEMA \"s.dot\"");
			statement.execute("CREATE TABLE \"s.dot\".data(i INTEGER)");
			statement.execute("INSERT INTO \"s.dot\".data VALUES (42)");
			statement.execute("CREATE MACRO \"s.dot\".\"m.name\"(a,b:='x;y') AS a||b");
			statement.execute("CREATE MACRO \"s.dot\".over(a) AS a+1,(a,b) AS a+b");
			statement.execute("CREATE MACRO \"s.dot\".tab(n:=3) AS TABLE SELECT * FROM range(n)");
			final com.sqlapp.data.schemas.Schema schema = com.sqlapp.data.schemas.SchemaUtils
					.readXml(new java.io.StringReader(dialect.readSchema(connection, "s.dot").asXml()));
			schema.setDialect(dialect);
			assertEquals(3, schema.getFunctions().size());
			for (var function : schema.getFunctions()) {
				assertNull(function.getSpecifics().get(DuckDbFunctionReader.INCOMPLETE_SIGNATURE));
				assertTrue(definition(function).contains("CREATE MACRO"));
			}
			assertEquals(42, scalar(statement, "SELECT i FROM \"s.dot\".data"));
			assertEquals(0, scalar(statement,
					"SELECT count(*) FROM duckdb_databases() WHERE database_name LIKE 'sqlapp_macro_%'"));
			final var registry = dialect.createSqlFactoryRegistry();
			for (var function : schema.getFunctions())
				execute(statement, registry.createSql(function, SqlType.DROP));
			for (var function : schema.getFunctions())
				execute(statement, registry.createSql(function, SqlType.CREATE));
			try (var rs = statement.executeQuery(
					"SELECT \"s.dot\".\"m.name\"('a'), \"s.dot\".over(41), \"s.dot\".over(40,2), (SELECT count(*) FROM \"s.dot\".tab())")) {
				assertTrue(rs.next());
				assertEquals("ax;y", rs.getString(1));
				assertEquals(42, rs.getInt(2));
				assertEquals(42, rs.getInt(3));
				assertEquals(3, rs.getInt(4));
			}
		}
	}

	private static int scalar(Statement statement, String sql) throws Exception {
		try (var rs = statement.executeQuery(sql)) {
			assertTrue(rs.next());
			return rs.getInt(1);
		}
	}

	@Test
	void completeDefinitionsSupportAttachedCatalogAndSharedScalarTableName() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:duckdb:");
				var statement = connection.createStatement()) {
			final var dialect = DialectResolver.getInstance().getDialect(connection);
			org.junit.jupiter.api.Assumptions
					.assumeTrue(dialect instanceof com.sqlapp.data.db.dialect.duckdb.DuckDbVersions.DuckDb20);
			statement.execute("ATTACH ':memory:' AS aux");
			statement.execute("CREATE MACRO aux.main.same(a:=42) AS a");
			statement.execute("CREATE MACRO aux.main.same(n:=3) AS TABLE SELECT * FROM range(n)");
			final var reader = (DuckDbFunctionReader) dialect.getCatalogReader().getSchemaReader().getFunctionReader();
			reader.setCatalogName("aux");
			reader.setSchemaName("main");
			reader.setReadCompleteDefinitions(true);
			final var functions = reader.getAllFull(connection);
			assertEquals(2, functions.size());
			for (var function : functions)
				execute(statement, dialect.createSqlFactoryRegistry().createSql(function, SqlType.DROP));
			for (var function : functions)
				execute(statement, dialect.createSqlFactoryRegistry().createSql(function, SqlType.CREATE));
			assertEquals(42, scalar(statement, "SELECT aux.main.same()"));
			assertEquals(3, scalar(statement, "SELECT count(*) FROM aux.main.same()"));
			assertEquals(0, scalar(statement,
					"SELECT count(*) FROM duckdb_databases() WHERE database_name LIKE 'sqlapp_macro_%'"));
		}
	}

	@Test
	void completeSchemaRejectsCallerTransactionWithoutCommittingIt() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:duckdb:");
				var statement = connection.createStatement()) {
			final var dialect = assertInstanceOf(DuckDb.class, DialectResolver.getInstance().getDialect(connection));
			org.junit.jupiter.api.Assumptions
					.assumeTrue(dialect instanceof com.sqlapp.data.db.dialect.duckdb.DuckDbVersions.DuckDb20);
			statement.execute("CREATE TABLE t(i INTEGER)");
			connection.setAutoCommit(false);
			statement.execute("INSERT INTO t VALUES (42)");
			assertThrows(java.sql.SQLException.class, () -> dialect.readSchema(connection, "main"));
			connection.rollback();
			assertEquals(0, scalar(statement, "SELECT count(*) FROM t"));
		}
	}

	@Test
	void selfReferencingForeignKeysDoNotPreventMacroExport() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:duckdb:");
				var statement = connection.createStatement()) {
			final var dialect = DialectResolver.getInstance().getDialect(connection);
			org.junit.jupiter.api.Assumptions
					.assumeTrue(dialect instanceof com.sqlapp.data.db.dialect.duckdb.DuckDbVersions.DuckDb20);
			statement.execute("CREATE TABLE tree(id INTEGER PRIMARY KEY, parent INTEGER REFERENCES tree(id))");
			statement.execute("CREATE TABLE child(id INTEGER REFERENCES tree(id))");
			statement.execute("INSERT INTO tree VALUES (1,NULL)");
			statement.execute("INSERT INTO child VALUES (1)");
			statement.execute("CREATE MACRO m(a:=42) AS a");
			statement.execute("CREATE MACRO branches(n:=3) AS TABLE SELECT * FROM tree WHERE id<n");
			final var definitions = DuckDbMacroDefinitions.read(connection, dialect, connection.getCatalog());
			assertEquals(2, definitions.size());
			statement.execute("DROP MACRO m");
			statement.execute(definitions.get(new DuckDbMacroDefinitions.Key("main", "m", false)));
			statement.execute("DROP MACRO TABLE branches");
			statement.execute(definitions.get(new DuckDbMacroDefinitions.Key("main", "branches", true)));
			assertEquals(42, scalar(statement, "SELECT m()"));
			assertEquals(1, scalar(statement, "SELECT count(*) FROM tree"));
			assertEquals(1, scalar(statement, "SELECT count(*) FROM child"));
			assertEquals(1, scalar(statement, "SELECT count(*) FROM branches()"));
			assertEquals(0, scalar(statement,
					"SELECT count(*) FROM duckdb_databases() WHERE database_name LIKE 'sqlapp_macro_%'"));
		}
	}

	@Test
	void failedSchemaCopyDetachesItsTemporaryCatalog() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:duckdb:");
				var statement = connection.createStatement()) {
			final var dialect = DialectResolver.getInstance().getDialect(connection);
			org.junit.jupiter.api.Assumptions
					.assumeTrue(dialect instanceof com.sqlapp.data.db.dialect.duckdb.DuckDbVersions.DuckDb20);
			assertThrows(java.sql.SQLException.class,
					() -> DuckDbMacroDefinitions.read(connection, dialect, "missing_catalog"));
			assertEquals(0, scalar(statement,
					"SELECT count(*) FROM duckdb_databases() WHERE database_name LIKE 'sqlapp_macro_%'"));
			assertEquals(42, scalar(statement, "SELECT 42"));
		}
	}

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
			assertEquals("true", select.getSpecifics().get(DuckDbFunctionReader.INCOMPLETE_SIGNATURE));
			assertEquals("CREATE MACRO memory.s1.answer() AS 42", definition(find(functions, "answer")));
			final Function table = find(functions, "t");
			assertEquals(FunctionType.Table, table.getFunctionType());
			assertEquals("true", table.getSpecifics().get(DuckDbFunctionReader.INCOMPLETE_SIGNATURE));
			if (overloads) {
				final Function over = find(functions, "over");
				assertTrue(over.getArguments().isEmpty());
				assertNull(over.getStatement() == null || over.getStatement().isEmpty() ? null : over.getStatement());
				assertEquals("true", over.getSpecifics().get(DuckDbFunctionReader.INCOMPLETE_SIGNATURE));
			}

			final SqlFactoryRegistry registry = dialect.createSqlFactoryRegistry();
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(select, SqlType.CREATE));
			// Parameter defaults cannot be recovered from catalog metadata. Only a
			// caller-supplied complete definition can authorize recreation.
			select.setDefinition("CREATE MACRO s1.\"Select\"(\"from\", b) AS \"from\" + b");
			table.setDefinition("CREATE MACRO s1.t(n) AS TABLE SELECT * FROM range(n)");
			if (overloads)
				find(functions, "over").setDefinition("CREATE MACRO s1.over(a) AS a + 1, (a,b) AS a+b");
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
				final Function reread = find(again, function.getName());
				assertEquals(function.getArguments().size(), reread.getArguments().size());
				assertEquals(function.getSpecifics(), reread.getSpecifics());
				if (!"true".equals(reread.getSpecifics().get(DuckDbFunctionReader.INCOMPLETE_SIGNATURE))) {
					assertEquals(definition(function), definition(reread));
				}
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

	@Test
	void defaultParametersRequireTheOriginalDefinitionInsteadOfBecomingRequired() throws Exception {
		try (Connection connection = DriverManager.getConnection("jdbc:duckdb:");
				Statement statement = connection.createStatement()) {
			final Dialect dialect = DialectResolver.getInstance().getDialect(connection);
			final String original = "CREATE MACRO s1.add_default(a,b:=5) AS a+b";
			statement.execute("CREATE SCHEMA s1");
			statement.execute(original);
			final Function function = functions(dialect, connection).getFirst();
			assertEquals("true", function.getSpecifics().get(DuckDbFunctionReader.INCOMPLETE_SIGNATURE));
			final var registry = dialect.createSqlFactoryRegistry();
			final var error = assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(function, SqlType.CREATE));
			assertTrue(error.getMessage().contains("complete original CREATE MACRO"));
			statement.execute("DROP MACRO s1.add_default");
			function.setDefinition(original);
			execute(statement, registry.createSql(function, SqlType.CREATE));
			try (var rs = statement.executeQuery("SELECT s1.add_default(37), s1.add_default(37,b:=7)")) {
				assertTrue(rs.next());
				assertEquals(42, rs.getInt(1));
				assertEquals(44, rs.getInt(2));
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
