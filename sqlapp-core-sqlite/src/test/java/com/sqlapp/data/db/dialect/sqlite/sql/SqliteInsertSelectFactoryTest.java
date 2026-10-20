/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;

class SqliteInsertSelectFactoryTest {
	private Table read(Connection connection, String schema, String name) {
		var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setSchemaName(schema);
		reader.setObjectName(name);
		return reader.getAllFull(connection).get(0);
	}

	@Test
	void conditionalInsertKeepsExistingRowsAndUsesGeneratedDefaults() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute(
					"CREATE TABLE t(id INTEGER PRIMARY KEY, key TEXT NOT NULL UNIQUE, value INT, derived INT AS (value+1))");
			var table = read(connection, "main", "t");
			var row = table.getRows().newElement();
			row.put("key", "same");
			row.put("value", 3);
			table.getRows().add(row);
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			var node = registry.createSqlNodes(table, SqlType.INSERT_SELECT_NOT_EXISTS).get(0);
			for (int i = 0; i < 2; i++) {
				try (var parameters = node.eval(row); var insert = parameters.createStatement(connection)) {
					parameters.setBind(insert);
					assertEquals(i == 0 ? 1 : 0, insert.executeUpdate());
				}
				row.put("value", 99);
			}
			try (var result = statement.executeQuery("SELECT id,value,derived FROM t")) {
				assertTrue(result.next());
				assertEquals(1, result.getInt(1));
				assertEquals(3, result.getInt(2));
				assertEquals(4, result.getInt(3));
				assertFalse(result.next());
			}
		}
	}

	@Test
	void conditionalInsertHandlesCompositeKeysAndAttachedNamesBeforeUpsertVersions() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS other");
			statement.execute("CREATE TABLE other.t(a INT,b TEXT,value INT,PRIMARY KEY(a,b))");
			statement.execute("CREATE TEMP TABLE t(a INT,b TEXT,value INT)");
			var table = read(connection, "other", "t");
			var row = table.getRows().newElement();
			row.put("a", 2);
			row.put("b", "key");
			row.put("value", 7);
			table.getRows().add(row);
			var registry = DialectResolver.getInstance().getDialect("SQLite", 3, 7, 0).createSqlFactoryRegistry();
			var node = registry.createSqlNodes(table, SqlType.INSERT_SELECT_NOT_EXISTS).get(0);
			for (int i = 0; i < 2; i++) {
				try (var parameters = node.eval(row); var insert = parameters.createStatement(connection)) {
					parameters.setBind(insert);
					assertEquals(i == 0 ? 1 : 0, insert.executeUpdate());
				}
			}
			try (var result = statement.executeQuery("SELECT count(*) FROM temp.t")) {
				assertTrue(result.next());
				assertEquals(0, result.getInt(1));
			}
		}
	}

	@Test
	void conditionalInsertDoesNotHideOtherConstraintViolations() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(k TEXT PRIMARY KEY,value INT CHECK(value>0))");
			var table = read(connection, "main", "t");
			var row = table.getRows().newElement();
			row.put("k", "new");
			row.put("value", -1);
			table.getRows().add(row);
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			var node = registry.createSqlNodes(table, SqlType.INSERT_SELECT_NOT_EXISTS).get(0);
			try (var parameters = node.eval(row); var insert = parameters.createStatement(connection)) {
				parameters.setBind(insert);
				assertThrows(java.sql.SQLException.class, insert::executeUpdate);
			}
		}
	}

	@Test
	void appendSelectUsesTemporarySourceAndFailsInsteadOfUpdatingConflicts() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(k TEXT PRIMARY KEY,value INT DEFAULT 5,derived INT AS(value+1))");
			statement.execute("CREATE TEMP TABLE t_temp(k TEXT,value INT)");
			statement.execute("INSERT INTO temp.t_temp VALUES('a',NULL),('b',9)");
			statement.execute("CREATE TABLE t_temp(k TEXT,value INT)");
			statement.execute("INSERT INTO main.t_temp VALUES('wrong',999)");
			var table = read(connection, "main", "t");
			var registry = DialectResolver.getInstance().getDialect("SQLite", 3, 7, 0).createSqlFactoryRegistry();
			String sql = registry.createSql(table, SqlType.INSERT_SELECT_TABLE).get(0).getSqlText();
			assertEquals(2, statement.executeUpdate(sql));
			statement.execute("UPDATE temp.t_temp SET value=50");
			assertThrows(java.sql.SQLException.class, () -> statement.executeUpdate(sql));
			try (var result = statement.executeQuery("SELECT value,derived FROM main.t WHERE k='a'")) {
				assertTrue(result.next());
				assertEquals(5, result.getInt(1));
				assertEquals(6, result.getInt(2));
			}
		}
	}

	@Test
	void appendSelectCanGenerateIdentitiesWithExplicitColumnSelection() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS other");
			statement.execute("CREATE TABLE t(id INTEGER PRIMARY KEY, value INT)");
			statement.execute("CREATE TABLE other.staged(incoming INT)");
			statement.execute("INSERT INTO other.staged VALUES(3),(4)");
			var table = read(connection, "main", "t");
			var source = read(connection, "other", "staged");
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			registry.getTableOptions().setInsertableColumn(c -> !c.isIdentity());
			registry.getTableOptions().setInsertTableColumnValue(c -> "_source_.incoming * 2");
			var factory = (SqliteInsertSelectTableFactory) registry.getSqlFactory(table, SqlType.INSERT_SELECT_TABLE);
			String sql = factory.createSql(table, source).get(0).getSqlText();
			assertEquals(2, statement.executeUpdate(sql));
			try (var result = statement.executeQuery("SELECT sum(id),sum(value) FROM t")) {
				assertTrue(result.next());
				assertEquals(3, result.getInt(1));
				assertEquals(14, result.getInt(2));
			}
		}
	}

	@Test
	void conditionalInsertPreservesExplicitIdentityKeys() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(id INTEGER PRIMARY KEY,value INT)");
			var table = read(connection, "main", "t");
			var row = table.getRows().newElement();
			row.put("id", 40);
			row.put("value", 7);
			table.getRows().add(row);
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			var node = registry.createSqlNodes(table, SqlType.INSERT_SELECT_NOT_EXISTS).get(0);
			for (int i = 0; i < 2; i++) {
				try (var parameters = node.eval(row); var insert = parameters.createStatement(connection)) {
					parameters.setBind(insert);
					assertEquals(i == 0 ? 1 : 0, insert.executeUpdate());
				}
			}
			try (var result = statement.executeQuery("SELECT id,value FROM t")) {
				assertTrue(result.next());
				assertEquals(40, result.getInt(1));
				assertEquals(7, result.getInt(2));
				assertFalse(result.next());
			}
		}
	}

	@Test
	void invalidConditionalKeysAndMissingStagingFailClearly() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE no_key(value INT)");
			statement.execute("CREATE TABLE identity_only(id INTEGER PRIMARY KEY)");
			statement.execute("CREATE TABLE t(k TEXT PRIMARY KEY)");
			statement.execute("CREATE TABLE t_temp(k TEXT)");
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(read(connection, "main", "no_key"), SqlType.INSERT_SELECT_NOT_EXISTS));
			assertThrows(IllegalArgumentException.class, () -> registry
					.createSql(read(connection, "main", "identity_only"), SqlType.INSERT_SELECT_NOT_EXISTS));
			String sql = registry.createSql(read(connection, "main", "t"), SqlType.INSERT_SELECT_TABLE).get(0)
					.getSqlText();
			assertThrows(java.sql.SQLException.class, () -> statement.executeUpdate(sql));
		}
	}
}
