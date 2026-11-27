/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Table;

class SqliteAlterCheckConstraintTest {
	private Table read(Connection connection, String schema) {
		var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setSchemaName(schema);
		reader.setObjectName("t");
		return reader.getAllFull(connection).get(0);
	}

	private void execute(Connection connection, List<SqlOperation> operations) throws SQLException {
		for (var operation : operations)
			try (var statement = connection.createStatement()) {
				statement.execute(operation.getSqlText());
			}
	}

	@Test
	void standaloneAddAndDropKeepQuotedNamesAndAttachedIdentity() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS \"Other]Db\"");
			statement.execute("CREATE TABLE main.t(value INT)");
			statement.execute("CREATE TABLE \"Other]Db\".t(value INT)");
			var table = read(connection, "Other]Db");
			var check = new CheckConstraint("positive] check", "value>0");
			table.getConstraints().add(check);
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			execute(connection, registry.createSql(check, SqlType.CREATE));
			assertNotNull(read(connection, "Other]Db").getConstraints().get(check.getName()));
			assertThrows(SQLException.class, () -> statement.execute("INSERT INTO \"Other]Db\".t VALUES(-1)"));
			statement.execute("INSERT INTO main.t VALUES(-1)");
			execute(connection, registry.createSql(check, SqlType.DROP));
			assertTrue(read(connection, "Other]Db").getConstraints().isEmpty());
			statement.execute("INSERT INTO \"Other]Db\".t VALUES(-1)");
		}
	}

	@Test
	void tableDifferencesAddReplaceAndRemoveChecksWithoutRebuildingRows() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(value INT)");
			statement.execute("INSERT INTO t VALUES(5)");
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			var original = read(connection, "main");
			var target = original.clone();
			target.getConstraints().add(new CheckConstraint("positive", "value>0"));
			execute(connection, registry.createSql(original.diff(target), SqlType.ALTER));
			original = read(connection, "main");
			target = original.clone();
			((CheckConstraint) target.getConstraints().get("positive")).setExpression("value>=0");
			var replacement = registry.createSql(original.diff(target), SqlType.ALTER);
			assertEquals(2, replacement.size());
			execute(connection, replacement);
			statement.execute("INSERT INTO t VALUES(0)");
			original = read(connection, "main");
			target = original.clone();
			target.getConstraints().remove("positive");
			execute(connection, registry.createSql(original.diff(target), SqlType.ALTER));
			statement.execute("INSERT INTO t VALUES(-1)");
			try (var result = statement.executeQuery("SELECT count(*),max(value) FROM t")) {
				assertTrue(result.next());
				assertEquals(3, result.getInt(1));
				assertEquals(5, result.getInt(2));
			}
		}
	}

	@Test
	void failedValidationLeavesTheOriginalSchemaAndCallerCanRollBackReplacement() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(value INT CONSTRAINT positive CHECK(value>0))");
			statement.execute("INSERT INTO t VALUES(5)");
			var original = read(connection, "main");
			var target = original.clone();
			((CheckConstraint) target.getConstraints().get("positive")).setExpression("value>10");
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			var operations = registry.createSql(original.diff(target), SqlType.ALTER);
			connection.setAutoCommit(false);
			assertThrows(SQLException.class, () -> execute(connection, operations));
			assertFalse(connection.getAutoCommit());
			connection.rollback();
			assertEquals("value>0",
					((CheckConstraint) read(connection, "main").getConstraints().get("positive")).getExpression());
			assertThrows(SQLException.class, () -> statement.execute("INSERT INTO t VALUES(-1)"));
		}
	}

	@Test
	void anonymousNamesAreMarkedAndCannotBeUsedForNativeDrop() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(value INT CHECK(value>0))");
			var table = read(connection, "main");
			var check = (CheckConstraint) table.getConstraints().get(0);
			assertEquals("true", check.getSpecifics().get("sqlite.check.unnamed"));
			Table copy = com.sqlapp.data.schemas.SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			assertEquals("true", copy.getConstraints().get(0).getSpecifics().get("sqlite.check.unnamed"));
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(check, SqlType.DROP));
			var target = table.clone();
			target.getConstraints().clear();
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(table.diff(target), SqlType.ALTER));
		}
	}

	@Test
	void oldUnknownAndUnfixedVersionsRejectNativeChanges() {
		var resolver = DialectResolver.getInstance();
		for (Integer revision : new Integer[] { null, 0 }) {
			var dialect = resolver.getDialect("SQLite", 3, 53, revision);
			var table = new Table("t").setSchemaName("main").setDialect(dialect);
			var check = new CheckConstraint("positive", "value>0");
			table.getConstraints().add(check);
			var registry = dialect.createSqlFactoryRegistry();
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(check, SqlType.CREATE));
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(check, SqlType.DROP));
		}
		var check = new CheckConstraint("positive", "value>0");
		assertThrows(IllegalArgumentException.class,
				() -> DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(check, SqlType.CREATE));
	}
}
