/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.schemas.Schema;

class SqliteOptimizeBuilderTest {
	@Test
	void updatesOnlyAttachedStatisticsAndRestoresTheConnectionAnalysisLimit() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			execute(connection, "ATTACH ':memory:' AS \"Other]Db\"");
			for (String schema : new String[] { "main", "\"Other]Db\"" }) {
				execute(connection, "CREATE TABLE " + schema + ".t(id INT,value TEXT)");
				execute(connection, "CREATE INDEX " + schema + ".idx ON t(value)");
				execute(connection,
						"WITH RECURSIVE n(x) AS(SELECT 1 UNION ALL SELECT x+1 FROM n WHERE x<100) INSERT INTO " + schema
								+ ".t SELECT x,'value-'||x FROM n");
			}
			execute(connection, "PRAGMA analysis_limit=17");
			var builder = new SqliteOptimizeBuilder(DialectHolder.defaultDialect).schema(new Schema("Other]Db"))
					.includeAllTables(true);
			execute(connection, builder.build());
			try (var statement = connection.createStatement();
					var result = statement.executeQuery("SELECT stat FROM \"Other]Db\".sqlite_stat1 WHERE idx='idx'")) {
				assertTrue(result.next());
				assertNotNull(result.getString(1));
				assertFalse(result.next());
			}
			assertEquals(0, scalar(connection, "SELECT count(*) FROM main.sqlite_master WHERE name='sqlite_stat1'"));
			assertEquals(17, scalar(connection, "PRAGMA analysis_limit"));
			assertEquals(100, scalar(connection, "SELECT count(*) FROM \"Other]Db\".t"));
			execute(connection, builder.includeAllTables(false).build());
			assertEquals(17, scalar(connection, "PRAGMA analysis_limit"));
		}
	}

	@Test
	void defaultMainExecutesAndTransactionalStatisticsRemainUnderCallerControl() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			execute(connection, "CREATE TABLE t(value TEXT)");
			execute(connection, "CREATE INDEX idx ON t(value)");
			execute(connection, "INSERT INTO t VALUES('kept')");
			var builder = new SqliteOptimizeBuilder(DialectHolder.defaultDialect);
			assertTrue(builder.build().endsWith(".optimize"));
			connection.setAutoCommit(false);
			execute(connection, builder.includeAllTables(true).build());
			assertFalse(connection.getAutoCommit());
			assertEquals(1, scalar(connection, "SELECT count(*) FROM sqlite_stat1 WHERE idx='idx'"));
			connection.rollback();
			assertEquals(0, scalar(connection, "SELECT count(*) FROM sqlite_master WHERE name='sqlite_stat1'"));
			assertEquals(1, scalar(connection, "SELECT count(*) FROM t"));
			connection.setAutoCommit(true);
			execute(connection, builder.includeAllTables(false).build());
		}
	}

	@Test
	void invalidSchemaNamesCannotExpandTheOperationOrExecuteOtherStatements() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			execute(connection, "CREATE TABLE kept(id INT)");
			var builder = new SqliteOptimizeBuilder(DialectHolder.defaultDialect)
					.schema("missing; DROP TABLE kept; --");
			assertThrows(SQLException.class, () -> execute(connection, builder.build()));
			assertEquals(0, scalar(connection, "SELECT count(*) FROM kept"));
		}
	}

	@Test
	void validatesOptionsAndHonorsBothVersionBoundaries() {
		var resolver = DialectResolver.getInstance();
		var builder = new SqliteOptimizeBuilder(DialectHolder.defaultDialect);
		for (String value : new String[] { null, " ", "bad\0name" })
			assertThrows(IllegalArgumentException.class, () -> builder.schema(value));
		assertThrows(NullPointerException.class, () -> new SqliteOptimizeBuilder(null));
		assertThrows(IllegalArgumentException.class,
				() -> new SqliteOptimizeBuilder(resolver.getDialect("PostgreSQL", 15, 0, 0)));
		var old = new SqliteOptimizeBuilder(resolver.getDialect("SQLite", 3, 17, 0));
		assertThrows(IllegalArgumentException.class, old::build);
		var first = new SqliteOptimizeBuilder(resolver.getDialect("SQLite", 3, 18, 0));
		assertEquals(builder.build(), first.build());
		var beforeAll = new SqliteOptimizeBuilder(resolver.getDialect("SQLite", 3, 45, 0)).includeAllTables(true);
		assertThrows(IllegalArgumentException.class, beforeAll::build);
		assertEquals(builder.build(), beforeAll.includeAllTables(false).build());
		var all = new SqliteOptimizeBuilder(resolver.getDialect("SQLite", 3, 46, 0)).includeAllTables(true);
		assertEquals(builder.includeAllTables(true).build(), all.build());
	}

	private void execute(Connection connection, String sql) throws SQLException {
		try (var statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}

	private int scalar(Connection connection, String sql) throws SQLException {
		try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
			assertTrue(result.next());
			return result.getInt(1);
		}
	}
}
