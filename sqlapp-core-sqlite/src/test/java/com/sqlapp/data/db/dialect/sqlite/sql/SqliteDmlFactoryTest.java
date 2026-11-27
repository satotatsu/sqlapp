/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.sql.ColumnSelectionStrategy;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;

class SqliteDmlFactoryTest {
	private Table read(Connection connection, String name, String schema) {
		var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setSchemaName(schema);
		reader.setObjectName(name);
		return reader.getAllFull(connection).get(0);
	}

	private String sql(Table table, SqlType type) {
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		registry.getTableOptions().setParameterExpression((column, value) -> "?");
		return registry.createSql(table, type).get(0).getSqlText();
	}

	@Test
	void insertsDefaultOnlyRowsAndExplicitIdentityWithoutChangingTheModel() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(id INTEGER PRIMARY KEY AUTOINCREMENT)");
			var table = read(connection, "t", "main");
			String defaultSql = sql(table, SqlType.INSERT);
			assertTrue(defaultSql.contains("DEFAULT VALUES"));
			statement.execute(defaultSql);
			assertThrows(IllegalArgumentException.class, () -> sql(table, SqlType.INSERT_ROWS));
			var row = table.getRows().newElement();
			row.put("id", 40);
			table.getRows().add(row);
			try (var insert = connection.prepareStatement(sql(table, SqlType.INSERT))) {
				insert.setInt(1, 40);
				insert.executeUpdate();
			}
			assertTrue(table.getColumns().get("id").isIdentity());
			table.getRows().clear();
			statement.execute(sql(table, SqlType.INSERT));
			try (var result = statement.executeQuery("SELECT group_concat(id) FROM t")) {
				assertTrue(result.next());
				assertEquals("1,40,41", result.getString(1));
			}
		}
	}

	@Test
	void mergeInsertsAndUpdatesCompositeKeysInAnAttachedDatabase() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS other");
			statement.execute(
					"CREATE TABLE other.\"order\"(a TEXT, b INT, value TEXT, computed TEXT GENERATED ALWAYS AS (upper(value)), PRIMARY KEY(a,b)) WITHOUT ROWID");
			var table = read(connection, "order", "other");
			String mergeSql = sql(table, SqlType.MERGE);
			assertTrue(mergeSql.contains("ON CONFLICT"));
			assertFalse(mergeSql.contains("computed"));
			try (var merge = connection.prepareStatement(mergeSql)) {
				merge.setString(1, "key");
				merge.setInt(2, 1);
				merge.setString(3, "first");
				assertEquals(1, merge.executeUpdate());
				merge.setString(3, "updated");
				assertEquals(1, merge.executeUpdate());
			}
			try (var result = statement.executeQuery("SELECT value, computed FROM other.\"order\"")) {
				assertTrue(result.next());
				assertEquals("updated", result.getString(1));
				assertEquals("UPDATED", result.getString(2));
				assertFalse(result.next());
			}
		}
	}

	@Test
	void keyOnlyMergeDoesNothingOnConflictAndPreservesForeignKeyChildren() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("PRAGMA foreign_keys=ON");
			statement.execute("CREATE TABLE parent(k TEXT PRIMARY KEY)");
			statement.execute("CREATE TABLE child(k TEXT REFERENCES parent(k) ON DELETE CASCADE)");
			statement.execute("INSERT INTO parent VALUES ('kept')");
			statement.execute("INSERT INTO child VALUES ('kept')");
			String mergeSql = sql(read(connection, "parent", "main"), SqlType.MERGE);
			assertTrue(mergeSql.contains("DO NOTHING"));
			try (var merge = connection.prepareStatement(mergeSql)) {
				merge.setString(1, "kept");
				merge.executeUpdate();
			}
			try (var result = statement.executeQuery("SELECT count(*) FROM child")) {
				assertTrue(result.next());
				assertEquals(1, result.getInt(1));
			}
		}
	}

	@Test
	void insertRowsKeepsExpansionMarkersAndOmitsGeneratedColumns() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(value INT, computed INT AS (value+1))");
			String insertSql = sql(read(connection, "t", "main"), SqlType.INSERT_ROWS);
			assertTrue(insertSql.contains("/*VALUES*/"));
			assertTrue(insertSql.contains("/*END*/"));
			assertFalse(insertSql.contains("computed"));
			try (var insert = connection.prepareStatement(insertSql)) {
				insert.setInt(1, 3);
				insert.executeUpdate();
			}
		}
	}

	@Test
	void unsafeOrUnsupportedMergeRequestsFailBeforeSqlIsReturned() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(value INT)");
			var table = read(connection, "t", "main");
			assertThrows(IllegalArgumentException.class, () -> sql(table, SqlType.MERGE));
			statement.execute("CREATE TABLE keyed(k TEXT PRIMARY KEY, value INT)");
			var keyed = read(connection, "keyed", "main");
			var old = DialectResolver.getInstance().getDialect("SQLite", 3, 23, 0).createSqlFactoryRegistry();
			assertThrows(IllegalArgumentException.class, () -> old.createSql(keyed, SqlType.MERGE));
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			registry.getTableOptions().setMergeRowsWithDelete(true);
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(keyed, SqlType.MERGE));
			var full = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			full.getTableOptions().setMergeKeyColumnsMatchingStrategy(t -> ColumnSelectionStrategy.FULL);
			assertThrows(IllegalArgumentException.class, () -> full.createSql(table, SqlType.MERGE));
		}
	}

	@Test
	void mergeRowsExpandsThroughTheSharedParserWithOneConflictClause() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(k TEXT PRIMARY KEY, value INT, computed INT AS (value+1))");
			statement.execute("INSERT INTO t(k,value) VALUES ('a',0)");
			var table = read(connection, "t", "main");
			for (var key : java.util.List.of("a", "b")) {
				var row = table.getRows().newElement();
				row.put("k", key);
				row.put("value", 5);
				table.getRows().add(row);
			}
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			var node = registry.createSqlNodes(table, SqlType.MERGE_ROWS).get(0);
			try (var parameters = node.eval(table); var merge = parameters.createStatement(connection)) {
				assertEquals(1, parameters.getSql().split("ON CONFLICT", -1).length - 1);
				parameters.setBind(merge);
				assertEquals(2, merge.executeUpdate());
			}
			try (var result = statement.executeQuery("SELECT sum(value),sum(computed),count(*) FROM t")) {
				assertTrue(result.next());
				assertEquals(10, result.getInt(1));
				assertEquals(12, result.getInt(2));
				assertEquals(2, result.getInt(3));
			}
		}
	}

	@Test
	void defaultTemplatesBindAdjacentStringAndNumericColumns() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(a VARCHAR(30), b DECIMAL(19,0), c CHAR(1), d TEXT)");
			var table = read(connection, "t", "main");
			for (var column : table.getColumns())
				assertNotNull(DialectHolder.defaultDialect.getDbDataTypes().getDbType(column.getDataType()),
						column.getName() + ": " + column.getDataType());
			var row = table.getRows().newElement();
			row.put("a", "text");
			row.put("b", 1);
			row.put("c", "Y");
			row.put("d", "last");
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			var node = registry.createSqlNodes(table, SqlType.INSERT).get(0);
			try (var parameters = node.eval(row)) {
				try (var insert = parameters.createStatement(connection)) {
					parameters.setBind(insert);
					assertEquals(1, insert.executeUpdate());
				} catch (java.sql.SQLException e) {
					throw new AssertionError(parameters.getSql(), e);
				}
			}
		}
	}

	@Test
	void uniqueKeyMergePreservesTheExistingPrimaryKey() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(id TEXT PRIMARY KEY, natural TEXT NOT NULL UNIQUE, value INT)");
			statement.execute("INSERT INTO t VALUES ('original','same',1)");
			try (var merge = connection.prepareStatement(sql(read(connection, "t", "main"), SqlType.MERGE))) {
				merge.setString(1, "attempted");
				merge.setString(2, "same");
				merge.setInt(3, 2);
				merge.executeUpdate();
			}
			try (var result = statement.executeQuery("SELECT id,value FROM t")) {
				assertTrue(result.next());
				assertEquals("original", result.getString(1));
				assertEquals(2, result.getInt(2));
			}
		}
	}

	@Test
	void sharedUpdateOmitsGeneratedColumnsOnSupportedVersions() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(k TEXT PRIMARY KEY, value INT, computed INT AS (value+1))");
			statement.execute("INSERT INTO t(k,value) VALUES ('key',1)");
			String updateSql = sql(read(connection, "t", "main"), SqlType.UPDATE);
			assertFalse(updateSql.contains("computed"));
			try (var update = connection.prepareStatement(updateSql)) {
				update.setInt(1, 4);
				update.setString(2, "key");
				update.executeUpdate();
			}
			try (var result = statement.executeQuery("SELECT computed FROM t")) {
				assertTrue(result.next());
				assertEquals(5, result.getInt(1));
			}
		}
	}

	@Test
	void mergePreservesConstraintAndIndexCollations() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE constraint_key(k TEXT NOT NULL, value INT, UNIQUE(k COLLATE NOCASE))");
			statement.execute("CREATE TABLE index_key(k TEXT NOT NULL, value INT)");
			statement.execute("CREATE UNIQUE INDEX ix ON index_key(k COLLATE NOCASE DESC)");
			for (var name : java.util.List.of("constraint_key", "index_key")) {
				var table = read(connection, name, "main");
				try (var merge = connection.prepareStatement(sql(table, SqlType.MERGE))) {
					merge.setString(1, "first");
					merge.setInt(2, 1);
					merge.executeUpdate();
					merge.setString(1, "FIRST");
					merge.setInt(2, 2);
					merge.executeUpdate();
				}
				try (var result = statement.executeQuery("SELECT count(*),sum(value) FROM " + name)) {
					assertTrue(result.next());
					assertEquals(1, result.getInt(1));
					assertEquals(2, result.getInt(2));
				}
			}
		}
	}
}
