/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.node.SqlNode;

class SqliteUpdateDeleteFactoryTest {
	private Table read(Connection connection, String schema, String name) {
		var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setSchemaName(schema);
		reader.setObjectName(name);
		return reader.getAllFull(connection).get(0);
	}

	private int execute(Connection connection, SqlNode node, Row row) throws Exception {
		try (var parameters = node.eval(row); var statement = parameters.createStatement(connection)) {
			parameters.setBind(statement);
			return statement.executeUpdate();
		}
	}

	@Test
	void compositeKeyUpdateAndDeleteResolveTypedReferencesInAnAttachedDatabase() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS other");
			statement.execute("CREATE TABLE other.t(a INT,b TEXT,value INT,derived INT AS(value+1),PRIMARY KEY(a,b))");
			statement.execute("INSERT INTO other.t(a,b,value) VALUES(1,'one',2),(1,'two',8)");
			statement.execute("CREATE TEMP TABLE t(a INT,b TEXT,value INT)");
			statement.execute("INSERT INTO temp.t VALUES(1,'one',999)");
			var table = read(connection, "other", "t");
			var row = table.getRows().newElement();
			row.put("a", 1);
			row.put("b", "one");
			row.put("value", 5);
			table.getRows().add(row);
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			String sql = registry.createSql(table, SqlType.UPDATE).get(0).getSqlText();
			String assignments = sql.substring(0, sql.indexOf("WHERE"));
			assertFalse(assignments.contains(DialectHolder.defaultDialect.quote("a")));
			assertFalse(assignments.contains(DialectHolder.defaultDialect.quote("b")));
			assertFalse(assignments.contains("derived"));
			assertEquals(1, execute(connection, registry.createSqlNodes(table, SqlType.UPDATE).get(0), row));
			try (var result = statement.executeQuery("SELECT value,derived FROM other.t WHERE b='one'")) {
				assertTrue(result.next());
				assertEquals(5, result.getInt(1));
				assertEquals(6, result.getInt(2));
			}
			assertEquals(1, execute(connection, registry.createSqlNodes(table, SqlType.DELETE).get(0), row));
			try (var result = statement.executeQuery("SELECT b,value FROM other.t")) {
				assertTrue(result.next());
				assertEquals("two", result.getString(1));
				assertEquals(8, result.getInt(2));
				assertFalse(result.next());
			}
			try (var result = statement.executeQuery("SELECT value FROM temp.t")) {
				assertTrue(result.next());
				assertEquals(999, result.getInt(1));
			}
		}
	}

	@Test
	void optimisticLockIncrementAndStaleUpdateDeleteChecksArePreserved() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(k TEXT PRIMARY KEY,value INT,lock_version INT NOT NULL DEFAULT 0)");
			statement.execute("INSERT INTO t(k,value) VALUES('key',1)");
			var table = read(connection, "main", "t");
			var row = table.getRows().newElement();
			row.put("k", "key");
			row.put("value", 2);
			row.put("lock_version", 0);
			table.getRows().add(row);
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			var update = registry.createSqlNodes(table, SqlType.UPDATE).get(0);
			var delete = registry.createSqlNodes(table, SqlType.DELETE).get(0);
			assertEquals(1, execute(connection, update, row));
			assertEquals(0, execute(connection, update, row));
			assertEquals(0, execute(connection, delete, row));
			try (var result = statement.executeQuery("SELECT value,lock_version FROM t")) {
				assertTrue(result.next());
				assertEquals(2, result.getInt(1));
				assertEquals(1, result.getInt(2));
			}
			row.put("lock_version", 1);
			assertEquals(1, execute(connection, delete, row));
		}
	}

	@Test
	void keyOnlyGeneratedOnlyAndFilteredUpdatesFailBeforeReturningSql() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE key_only(k TEXT PRIMARY KEY)");
			statement.execute("CREATE TABLE computed(k TEXT PRIMARY KEY,derived INT AS(length(k)))");
			statement.execute("CREATE TABLE writable(k TEXT PRIMARY KEY,value INT)");
			for (var name : java.util.List.of("key_only", "computed")) {
				var table = read(connection, "main", name);
				for (var type : java.util.List.of(SqlType.UPDATE, SqlType.UPDATE_TABLE, SqlType.UPDATE_FOR_APP)) {
					var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
					assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, type));
				}
			}
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			registry.getTableOptions().setUpdateableColumn(false);
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(read(connection, "main", "writable"), SqlType.UPDATE));
		}
	}

	@Test
	void wholeTableUpdateRetainsItsExplicitCondition() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(k TEXT PRIMARY KEY,value INT)");
			statement.execute("INSERT INTO t VALUES('a',1),('b',2)");
			var table = read(connection, "main", "t");
			var row = table.getRows().newElement();
			row.put("value", 9);
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			registry.getTableOptions().setUpdateAllCondition(
					(t, builder) -> builder.and().name(t.getColumns().get("value")).eq()._add("1"));
			assertEquals(1, execute(connection, registry.createSqlNodes(table, SqlType.UPDATE_TABLE).get(0), row));
			try (var result = statement.executeQuery("SELECT value FROM t ORDER BY k")) {
				assertTrue(result.next());
				assertEquals(9, result.getInt(1));
				assertTrue(result.next());
				assertEquals(2, result.getInt(1));
			}
		}
	}

	@Test
	void updateRetainsCreatedAtAndUpdateableColumnSelections() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(k TEXT PRIMARY KEY,created TEXT,value INT,untouched INT)");
			statement.execute("INSERT INTO t VALUES('key','kept',1,2)");
			var table = read(connection, "main", "t");
			var row = table.getRows().newElement();
			row.put("k", "key");
			row.put("created", "attempted");
			row.put("value", 9);
			row.put("untouched", 999);
			table.getRows().add(row);
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			registry.getTableOptions().setCreatedAtColumn(c -> c.getName().equals("created"));
			registry.getTableOptions().setUpdateableColumn(c -> !c.getName().equals("untouched"));
			assertEquals(1, execute(connection, registry.createSqlNodes(table, SqlType.UPDATE).get(0), row));
			try (var result = statement.executeQuery("SELECT created,value,untouched FROM t")) {
				assertTrue(result.next());
				assertEquals("kept", result.getString(1));
				assertEquals(9, result.getInt(2));
				assertEquals(2, result.getInt(3));
			}
		}
	}

	@Test
	void defaultKeyBasedOperationsStillRejectKeylessModels() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(value INT)");
			var table = read(connection, "main", "t");
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			for (var type : java.util.List.of(SqlType.UPDATE, SqlType.DELETE))
				assertThrows(com.sqlapp.exceptions.MissingWhereClauseKeyException.class,
						() -> registry.createSql(table, type));
		}
	}

	@Test
	void legacyDialectUsesTheSameCanonicalDmlAndModelIsUnchanged() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(k TEXT PRIMARY KEY,value INT)");
			statement.execute("INSERT INTO t VALUES('key',1)");
			var table = read(connection, "main", "t");
			var row = table.getRows().newElement();
			row.put("k", "key");
			row.put("value", 3);
			table.getRows().add(row);
			String before = table.asXml();
			var registry = DialectResolver.getInstance().getDialect("SQLite", 3, 7, 0).createSqlFactoryRegistry();
			assertEquals(1, execute(connection, registry.createSqlNodes(table, SqlType.UPDATE).get(0), row));
			assertEquals(1, execute(connection, registry.createSqlNodes(table, SqlType.DELETE).get(0), row));
			assertEquals(before, table.asXml());
		}
	}
}
