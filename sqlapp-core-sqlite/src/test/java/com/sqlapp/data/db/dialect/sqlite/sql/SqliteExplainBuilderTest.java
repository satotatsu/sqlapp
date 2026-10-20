/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;

class SqliteExplainBuilderTest {
	@Test
	void generatedKeySelectRetainsSharedTypedBindings() throws Exception {
		var dialect = DialectHolder.defaultDialect;
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS other");
			statement.execute("CREATE TABLE other.t(id INT PRIMARY KEY,value TEXT)");
			var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setSchemaName("other");
			reader.setObjectName("t");
			var table = reader.getAllFull(connection).get(0);
			var row = table.getRows().newElement();
			row.put("id", 1);
			table.getRows().add(row);
			String select = dialect.createSqlFactoryRegistry().createSql(table, com.sqlapp.data.db.sql.SqlType.SELECT)
					.get(0).getSqlText();
			String explain = new SqliteExplainBuilder(dialect).statement(select).build();
			var node = com.sqlapp.jdbc.sql.SqlParser.getInstance().parse(dialect, explain);
			try (var parameters = node.eval(row); var query = parameters.createStatement(connection)) {
				assertEquals(1, parameters.getParameterSize());
				assertEquals(com.sqlapp.data.db.datatype.DataType.INT,
						parameters.getBindParameters().get(0).stream().findFirst().orElseThrow().getDataType());
				parameters.setBind(query);
				try (var result = query.executeQuery()) {
					assertTrue(result.next());
				}
			}
		}
	}

	@Test
	void defaultsToAParameterizedQueryPlanAndCanSwitchModes() throws Exception {
		var builder = new SqliteExplainBuilder(DialectHolder.defaultDialect)
				.statement("SELECT value FROM t WHERE id=?;");
		assertEquals("EXPLAIN QUERY PLAN SELECT value FROM t WHERE id=?", builder.build());
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(id INTEGER PRIMARY KEY,value TEXT)");
			statement.execute("INSERT INTO t VALUES(1,'kept')");
			try (var query = connection.prepareStatement(builder.build())) {
				query.setInt(1, 1);
				try (var result = query.executeQuery()) {
					assertTrue(result.next());
					assertNotNull(result.getString("detail"));
				}
			}
			assertTrue(builder.bytecode().build().startsWith("EXPLAIN SELECT"));
			try (var query = connection.prepareStatement(builder.build())) {
				query.setInt(1, 1);
				try (var result = query.executeQuery()) {
					assertTrue(result.next());
					assertNotNull(result.getString("opcode"));
				}
			}
			assertTrue(builder.queryPlan().build().startsWith("EXPLAIN QUERY PLAN"));
		}
	}

	@Test
	void explainingDmlDoesNotApplyTheRequestedWrites() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(id INT PRIMARY KEY,value TEXT)");
			statement.execute("INSERT INTO t VALUES(1,'kept')");
			for (String sql : List.of("INSERT INTO t VALUES(2,'new')", "UPDATE t SET value='changed' WHERE id=1",
					"DELETE FROM t", "REPLACE INTO t VALUES(1,'replaced')",
					"WITH x AS(SELECT 3 AS id) INSERT INTO t SELECT id,'cte' FROM x")) {
				var builder = new SqliteExplainBuilder(DialectHolder.defaultDialect).statement(sql);
				for (String explain : List.of(builder.build(), builder.bytecode().build())) {
					try (var result = statement.executeQuery(explain)) {
						while (result.next()) {
							/* Consume diagnostics. */ }
					}
				}
			}
			try (var result = statement.executeQuery("SELECT id,value FROM t")) {
				assertTrue(result.next());
				assertEquals(1, result.getInt(1));
				assertEquals("kept", result.getString(2));
				assertFalse(result.next());
			}
		}
	}

	@Test
	void commentsQuotedSemicolonsAndCtesRemainOneStatement() throws Exception {
		String sql = "/* before */WITH x AS(SELECT 'it''s; /* text */' AS value) SELECT value FROM x;-- after";
		var builder = new SqliteExplainBuilder(DialectHolder.defaultDialect).statement(sql);
		assertTrue(builder.build().contains("'it''s; /* text */'"));
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement();
				var result = statement.executeQuery(builder.build())) {
			assertTrue(result.next());
		}
	}

	@Test
	void unsupportedAndMultipleStatementsFailBeforeSqlIsReturned() {
		for (String sql : List.of("PRAGMA foreign_keys=OFF", "/* ignored */ PRAGMA user_version=7", "BEGIN", "COMMIT",
				"CREATE TABLE t(id INT)", "DROP TABLE t", "EXPLAIN SELECT 1", "SELECT 1; DELETE FROM t",
				"SELECT 1;/* gap */PRAGMA user_version=9", "-- comment only", ";;;")) {
			assertThrows(IllegalArgumentException.class,
					() -> new SqliteExplainBuilder(DialectHolder.defaultDialect).statement(sql).build(), sql);
		}
		assertThrows(IllegalArgumentException.class,
				() -> new SqliteExplainBuilder(DialectHolder.defaultDialect).build());
		assertThrows(IllegalArgumentException.class,
				() -> new SqliteExplainBuilder(DialectHolder.defaultDialect).statement(" "));
		assertThrows(IllegalArgumentException.class,
				() -> new SqliteExplainBuilder(DialectHolder.defaultDialect).statement(null));
		assertThrows(IllegalArgumentException.class,
				() -> new SqliteExplainBuilder(DialectResolver.getInstance().getDialect("PostgreSQL", 18, 0, 0)));
		assertThrows(NullPointerException.class, () -> new SqliteExplainBuilder(null));
		assertThrows(com.sqlapp.exceptions.InvalidTextException.class,
				() -> new SqliteExplainBuilder(DialectHolder.defaultDialect).statement("SELECT 'unfinished").build());
	}

	@Test
	void oldAndCurrentDialectSelectionsUseTheNativeExplainSyntax() {
		for (var dialect : List.of(DialectHolder.defaultDialect,
				DialectResolver.getInstance().getDialect("SQLite", 3, 7, 0))) {
			assertEquals("EXPLAIN QUERY PLAN SELECT 1",
					new SqliteExplainBuilder(dialect).statement("SELECT 1").build());
			assertEquals("EXPLAIN SELECT 1",
					new SqliteExplainBuilder(dialect).statement("SELECT 1").bytecode().build());
		}
	}
}
