/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Table;

class SqliteAlterDependencyOrderTest {
	@Test
	void replacesExpressionIndexBeforeDroppingItsOldColumnInAttachedDatabase() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS other");
			for (String schema : List.of("main", "other")) {
				statement.execute("CREATE TABLE " + schema + ".t(old_value INT, value INT)");
				statement.execute("CREATE INDEX " + schema + ".ix ON t(abs(old_value)) WHERE old_value>0");
				statement.execute("INSERT INTO " + schema + ".t VALUES(7,9)");
			}
			Table original = read(connection, "other");
			Table target = original.clone();
			target.getColumns().remove("old_value");
			var index = target.getIndexes().get("ix");
			index.getColumns().clear();
			index.getColumns().add("abs(value)");
			index.getSpecifics().put("sqlite.index.keys", "abs(value)");
			index.setWhere("value>0");
			var operations = plan(original, target);
			assertEquals(List.of(SqlType.DROP, SqlType.ALTER, SqlType.CREATE),
					operations.stream().map(SqlOperation::getSqlType).toList());
			execute(connection, operations);
			assertNull(read(connection, "other").getColumns().get("old_value"));
			assertNotNull(read(connection, "main").getColumns().get("old_value"));
			try (var rows = statement.executeQuery("SELECT value FROM other.t INDEXED BY ix WHERE value>0")) {
				assertTrue(rows.next());
				assertEquals(9, rows.getInt(1));
				assertFalse(rows.next());
			}
		}
	}

	@Test
	void replacesNamedCheckBeforeDroppingItsOldColumn() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(old_value INT, value INT, CONSTRAINT positive CHECK(old_value>0))");
			statement.execute("INSERT INTO t VALUES(7,9)");
			Table original = read(connection, "main");
			Table target = original.clone();
			target.getColumns().remove("old_value");
			((CheckConstraint) target.getConstraints().get("positive")).setExpression("value>0");
			var operations = plan(original, target);
			assertEquals(List.of(SqlType.DROP, SqlType.ALTER, SqlType.CREATE),
					operations.stream().map(SqlOperation::getSqlType).toList());
			execute(connection, operations);
			assertNull(read(connection, "main").getColumns().get("old_value"));
			assertThrows(SQLException.class, () -> statement.execute("INSERT INTO t VALUES(-1)"));
			try (var rows = statement.executeQuery("SELECT value FROM t")) {
				assertTrue(rows.next());
				assertEquals(9, rows.getInt(1));
			}
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void allOldDependenciesAreRemovedBeforeColumnChangesAndCallerRollbackRestoresThem(boolean invalidCheck)
			throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(old_value INT, value INT, CONSTRAINT positive CHECK(old_value>0))");
			statement.execute("CREATE INDEX ix ON t(old_value)");
			statement.execute("INSERT INTO t VALUES(7,9)");
			Table original = read(connection, "main");
			Table target = original.clone();
			target.getColumns().remove("old_value");
			((CheckConstraint) target.getConstraints().get("positive"))
					.setExpression(invalidCheck ? "value>100" : "value>0");
			var index = target.getIndexes().get("ix");
			index.getColumns().clear();
			index.getColumns().add("value");
			index.getSpecifics().put("sqlite.index.keys", "value");
			var operations = plan(original, target);
			assertEquals(List.of(SqlType.DROP, SqlType.DROP, SqlType.ALTER, SqlType.CREATE, SqlType.CREATE),
					operations.stream().map(SqlOperation::getSqlType).toList());
			connection.setAutoCommit(false);
			if (invalidCheck) {
				assertThrows(SQLException.class, () -> execute(connection, operations));
			} else {
				execute(connection, operations);
				assertNull(read(connection, "main").getColumns().get("old_value"));
				assertThrows(SQLException.class, () -> statement.execute("INSERT INTO t VALUES(-1)"));
				try (var rows = statement.executeQuery("SELECT value FROM t INDEXED BY ix")) {
					assertTrue(rows.next());
					assertEquals(9, rows.getInt(1));
					assertFalse(rows.next());
				}
			}
			assertFalse(connection.getAutoCommit());
			connection.rollback();
			Table restored = read(connection, "main");
			assertNotNull(restored.getColumns().get("old_value"));
			assertEquals("old_value>0", ((CheckConstraint) restored.getConstraints().get("positive")).getExpression());
			assertNotNull(restored.getIndexes().get("ix"));
			assertThrows(SQLException.class, () -> statement.execute("INSERT INTO t VALUES(-1,9)"));
			try (var rows = statement.executeQuery("SELECT old_value,value FROM t INDEXED BY ix")) {
				assertTrue(rows.next());
				assertEquals(7, rows.getInt(1));
				assertEquals(9, rows.getInt(2));
			}
		}
	}

	private Table read(Connection connection, String schema) {
		var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setSchemaName(schema);
		reader.setObjectName("t");
		return reader.getAllFull(connection).get(0);
	}

	private List<SqlOperation> plan(Table original, Table target) {
		return DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(original.diff(target), SqlType.ALTER);
	}

	private void execute(Connection connection, List<SqlOperation> operations) throws SQLException {
		for (var operation : operations)
			try (var statement = connection.createStatement()) {
				statement.execute(operation.getSqlText());
			}
	}
}
