/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;

class SqliteAlterNullabilityTest {
	private Table read(Connection connection, String schema) {
		var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setSchemaName(schema);
		reader.setObjectName("t");
		return reader.getAllFull(connection).get(0);
	}

	private String alter(Table original, Table target) {
		var operations = DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(original.diff(target),
				SqlType.ALTER);
		assertEquals(1, operations.size());
		return operations.get(0).getSqlText();
	}

	@Test
	void setAndDropNotNullRetainAttachedIdentityAndData() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS \"Other]Db\"");
			statement.execute("CREATE TABLE main.t(value TEXT)");
			statement.execute("CREATE TABLE \"Other]Db\".t(value TEXT)");
			statement.execute("INSERT INTO \"Other]Db\".t VALUES('kept')");
			var original = read(connection, "Other]Db");
			var target = original.clone();
			target.getColumns().get("value").setNotNull(true);
			statement.execute(alter(original, target));
			assertTrue(read(connection, "Other]Db").getColumns().get("value").isNotNull());
			assertThrows(SQLException.class, () -> statement.execute("INSERT INTO \"Other]Db\".t VALUES(NULL)"));
			statement.execute("INSERT INTO main.t VALUES(NULL)");
			original = read(connection, "Other]Db");
			target = original.clone();
			target.getColumns().get("value").setNullable(true);
			statement.execute(alter(original, target));
			assertFalse(read(connection, "Other]Db").getColumns().get("value").isNotNull());
			statement.execute("INSERT INTO \"Other]Db\".t VALUES(NULL)");
			try (var result = statement.executeQuery("SELECT count(*),max(value) FROM \"Other]Db\".t")) {
				assertTrue(result.next());
				assertEquals(2, result.getInt(1));
				assertEquals("kept", result.getString(2));
			}
		}
	}

	@Test
	void existingNullsCauseNativeFailureWithoutChangingTheColumn() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(value TEXT)");
			statement.execute("INSERT INTO t VALUES(NULL)");
			var original = read(connection, "main");
			var target = original.clone();
			target.getColumns().get("value").setNotNull(true);
			String sql = alter(original, target);
			assertThrows(SQLException.class, () -> statement.execute(sql));
			assertFalse(read(connection, "main").getColumns().get("value").isNotNull());
			try (var result = statement.executeQuery("SELECT value FROM t")) {
				assertTrue(result.next());
				assertNull(result.getString(1));
			}
		}
	}

	@Test
	void customConflictPolicyIsRenderedWhenSettingNotNull() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(value TEXT)");
			var original = read(connection, "main");
			original.getColumns().get("value").getSpecifics().put("sqlite.notNull.onConflict", "IGNORE");
			var target = original.clone();
			target.getColumns().get("value").setNotNull(true);
			statement.execute(alter(original, target));
			statement.execute("INSERT INTO t VALUES(NULL)");
			try (var result = statement.executeQuery("SELECT count(*) FROM t")) {
				assertTrue(result.next());
				assertEquals(0, result.getInt(1));
			}
			assertEquals("IGNORE",
					read(connection, "main").getColumns().get("value").getSpecifics().get("sqlite.notNull.onConflict"));
		}
	}

	@Test
	void nativeNullabilityChangesCanBeRolledBackByTheCaller() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(value TEXT)");
			statement.execute("INSERT INTO t VALUES('kept')");
			var original = read(connection, "main");
			var target = original.clone();
			target.getColumns().get("value").setNotNull(true);
			String sql = alter(original, target);
			connection.setAutoCommit(false);
			statement.execute(sql);
			assertTrue(read(connection, "main").getColumns().get("value").isNotNull());
			assertFalse(connection.getAutoCommit());
			connection.rollback();
			assertFalse(read(connection, "main").getColumns().get("value").isNotNull());
			try (var result = statement.executeQuery("SELECT value FROM t")) {
				assertTrue(result.next());
				assertEquals("kept", result.getString(1));
			}
		}
	}

	@Test
	void earlierTargetsAndCombinedDefinitionChangesFailBeforeSqlIsReturned() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(value TEXT)");
			var original = read(connection, "main");
			var target = original.clone();
			target.getColumns().get("value").setNotNull(true);
			var old = DialectResolver.getInstance().getDialect("SQLite", 3, 52, 0).createSqlFactoryRegistry();
			original.setDialect(old.getDialect());
			target.setDialect(old.getDialect());
			assertThrows(IllegalArgumentException.class, () -> old.createSql(original.diff(target), SqlType.ALTER));
			original.setDialect(DialectHolder.defaultDialect);
			target.setDialect(DialectHolder.defaultDialect);
			target.getColumns().get("value").setDefaultValue("'new'");
			assertThrows(IllegalArgumentException.class, () -> alter(original, target));
		}
	}
}
