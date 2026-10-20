/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

class SqliteMergeTableFactoryTest {
	private Table read(Connection connection, String schema, String name) {
		var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setSchemaName(schema);
		reader.setObjectName(name);
		return reader.getAllFull(connection).get(0);
	}

	@Test
	void defaultStagingUsesTempAndPreservesNaturalKeyIdentityAndDefaults() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute(
					"CREATE TABLE t(id INTEGER PRIMARY KEY, key TEXT NOT NULL UNIQUE, payload TEXT DEFAULT 'fallback', derived TEXT AS (upper(payload)))");
			statement.execute("INSERT INTO t(id,key,payload) VALUES(1,'existing','old')");
			statement.execute("CREATE TABLE t_temp(id INT, key TEXT, payload TEXT)");
			statement.execute("INSERT INTO t_temp VALUES(999,'wrong','wrong')");
			statement.execute("CREATE TEMP TABLE t(id INT, key TEXT, payload TEXT)");
			statement.execute("CREATE TEMP TABLE t_temp(id INT, key TEXT, payload TEXT)");
			statement.execute("INSERT INTO temp.t_temp VALUES(99,'existing','new'),(2,'added',NULL)");
			var target = read(connection, "main", "t");
			new Schema("main").getTables().add(target);
			String before = target.asXml();
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(false);
			registry.getTableOptions().setMergeTableWithReturning(true);
			var operations = registry.createSql(target, SqlType.MERGE_TABLE);
			assertEquals(SqlType.MERGE_TABLE, operations.get(0).getSqlType());
			Set<Integer> ids = new HashSet<>();
			try (var result = statement.executeQuery(operations.get(0).getSqlText())) {
				while (result.next())
					ids.add(result.getInt(1));
			}
			assertEquals(Set.of(1, 2), ids);
			assertEquals(before, target.asXml());
			try (var result = statement.executeQuery("SELECT id,payload,derived FROM main.t ORDER BY id")) {
				assertTrue(result.next());
				assertEquals(1, result.getInt(1));
				assertEquals("new", result.getString(2));
				assertEquals("NEW", result.getString(3));
				assertTrue(result.next());
				assertEquals("fallback", result.getString(2));
				assertFalse(result.next());
			}
			try (var result = statement.executeQuery("SELECT count(*) FROM temp.t")) {
				assertTrue(result.next());
				assertEquals(0, result.getInt(1));
			}
		}
	}

	@Test
	void explicitAttachedSourceSupportsColumnExpressionsAndEmptyInput() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS other");
			statement.execute("CREATE TABLE main.\"order\"(k TEXT PRIMARY KEY, value INT)");
			statement.execute("CREATE TABLE other.\"source rows\"(k TEXT, incoming INT)");
			var target = read(connection, "main", "order");
			var source = read(connection, "other", "source rows");
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			registry.getTableOptions().setInsertTableColumnValue(
					c -> c.getName().equals("value") ? "_source_.incoming * 2" : c.getName());
			var factory = (SqliteMergeTableFactory) registry.getSqlFactory(target, SqlType.MERGE_TABLE);
			String sql = factory.createSql(target, source).get(0).getSqlText();
			assertEquals(0, statement.executeUpdate(sql));
			statement.execute("INSERT INTO other.\"source rows\" VALUES('a',3),('b',4)");
			assertEquals(2, statement.executeUpdate(sql));
			statement.execute("UPDATE other.\"source rows\" SET incoming=5 WHERE k='a'");
			statement.executeUpdate(sql);
			try (var result = statement.executeQuery("SELECT sum(value), count(*) FROM main.\"order\"")) {
				assertTrue(result.next());
				assertEquals(18, result.getInt(1));
				assertEquals(2, result.getInt(2));
			}
		}
	}

	@Test
	void modeledSourceIsResolvedFromTheTargetSchema() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(k TEXT PRIMARY KEY, value INT)");
			statement.execute("CREATE TABLE staged(k TEXT, value INT)");
			statement.execute("INSERT INTO staged VALUES('key',7)");
			statement.execute("CREATE TEMP TABLE staged(k TEXT,value INT)");
			statement.execute("INSERT INTO temp.staged VALUES('wrong',999)");
			var schema = new Schema("main");
			var target = read(connection, "main", "t");
			schema.getTables().add(target);
			schema.getTables().add(read(connection, "main", "staged"));
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			registry.getTableOptions().setTempTableName(t -> "staged");
			statement.executeUpdate(registry.createSql(target, SqlType.MERGE_TABLE).get(0).getSqlText());
			try (var result = statement.executeQuery("SELECT k,value FROM main.t")) {
				assertTrue(result.next());
				assertEquals("key", result.getString(1));
				assertEquals(7, result.getInt(2));
			}
		}
	}

	@Test
	void missingTempStagingCannotFallBackToMain() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(k TEXT PRIMARY KEY)");
			statement.execute("CREATE TABLE t_temp(k TEXT)");
			statement.execute("INSERT INTO t_temp VALUES('wrong')");
			var target = read(connection, "main", "t");
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			String sql = registry.createSql(target, SqlType.MERGE_TABLE).get(0).getSqlText();
			assertThrows(java.sql.SQLException.class, () -> statement.executeUpdate(sql));
		}
	}

	@Test
	void singleAndMultiRowMergeReturningUseTheSharedParser() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(k TEXT PRIMARY KEY, value INT)");
			statement.execute("INSERT INTO t VALUES('existing',0)");
			var target = read(connection, "main", "t");
			for (var key : java.util.List.of("existing", "added")) {
				var row = target.getRows().newElement();
				row.put("k", key);
				row.put("value", 9);
				target.getRows().add(row);
			}
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			registry.getTableOptions().setMergeTableWithReturning(true);
			var node = registry.createSqlNodes(target, SqlType.MERGE_ROWS).get(0);
			Set<String> keys = new HashSet<>();
			try (var parameters = node.eval(target); var merge = parameters.createStatement(connection)) {
				parameters.setBind(merge);
				try (var result = merge.executeQuery()) {
					while (result.next())
						keys.add(result.getString(1));
				}
			}
			assertEquals(Set.of("existing", "added"), keys);
			var single = registry.createSqlNodes(target, SqlType.MERGE).get(0);
			try (var parameters = single.eval(target.getRows().get(0));
					var merge = parameters.createStatement(connection)) {
				parameters.setBind(merge);
				try (var result = merge.executeQuery()) {
					assertTrue(result.next());
					assertEquals("existing", result.getString(1));
					assertFalse(result.next());
				}
			}
		}
	}

	@Test
	void keyOnlyDoNothingReturningDoesNotInventAnUpdatedRow() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(k TEXT PRIMARY KEY)");
			statement.execute("CREATE TEMP TABLE t_temp(k TEXT)");
			statement.execute("INSERT INTO t VALUES('kept')");
			statement.execute("INSERT INTO t_temp VALUES('kept'),('added')");
			var target = read(connection, "main", "t");
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			registry.getTableOptions().setMergeTableWithReturning(true);
			try (var result = statement
					.executeQuery(registry.createSql(target, SqlType.MERGE_TABLE).get(0).getSqlText())) {
				assertTrue(result.next());
				assertEquals("added", result.getString(1));
				assertFalse(result.next());
			}
		}
	}

	@Test
	void constraintViolationRollsBackTheWholeStagedStatement() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(k TEXT PRIMARY KEY, value TEXT UNIQUE)");
			statement.execute("INSERT INTO t VALUES('a','old'),('z','reserved')");
			statement.execute("CREATE TEMP TABLE t_temp(k TEXT, value TEXT)");
			statement.execute("INSERT INTO t_temp VALUES('a','changed'),('b','reserved')");
			var target = read(connection, "main", "t");
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			registry.getTableOptions().setMergeKeyColumnsMatchingStrategy(
					t -> com.sqlapp.data.db.sql.ColumnSelectionStrategy.PRIMARY_KEY);
			String sql = registry.createSql(target, SqlType.MERGE_TABLE).get(0).getSqlText();
			assertThrows(java.sql.SQLException.class, () -> statement.executeUpdate(sql));
			try (var result = statement.executeQuery("SELECT value FROM t WHERE k='a'")) {
				assertTrue(result.next());
				assertEquals("old", result.getString(1));
			}
			try (var result = statement.executeQuery("SELECT count(*) FROM t")) {
				assertTrue(result.next());
				assertEquals(2, result.getInt(1));
			}
		}
	}

	@Test
	void returningRejectsOldTargetsAndUnsupportedAliases() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(k TEXT PRIMARY KEY, value INT)");
			var target = read(connection, "main", "t");
			for (var type : java.util.List.of(SqlType.MERGE, SqlType.MERGE_ROWS, SqlType.MERGE_TABLE)) {
				var old = DialectResolver.getInstance().getDialect("SQLite", 3, 34, 0).createSqlFactoryRegistry();
				old.getTableOptions().setMergeTableWithReturning(true);
				assertThrows(IllegalArgumentException.class, () -> old.createSql(target, type));
				var aliases = DialectHolder.defaultDialect.createSqlFactoryRegistry();
				aliases.getTableOptions().setMergeTableWithReturning(true);
				aliases.getTableOptions().setReturningOldAlias("old_row");
				assertThrows(IllegalArgumentException.class, () -> aliases.createSql(target, type));
			}
		}
	}

	@Test
	void invalidSourcesAndUnsupportedVersionsFailClearly() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(k TEXT PRIMARY KEY, value INT)");
			var target = read(connection, "main", "t");
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			var factory = (SqliteMergeTableFactory) registry.getSqlFactory(target, SqlType.MERGE_TABLE);
			assertThrows(IllegalArgumentException.class, () -> factory.createSql(target, target));
			var source = target.clone().setName("staged");
			source.getColumns().remove("value");
			assertThrows(IllegalArgumentException.class, () -> factory.createSql(target, source));
			var old = DialectResolver.getInstance().getDialect("SQLite", 3, 23, 0).createSqlFactoryRegistry();
			assertThrows(IllegalArgumentException.class, () -> old.createSql(target, SqlType.MERGE_TABLE));
			var invalidName = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			invalidName.getTableOptions().setTempTableName(t -> "");
			assertThrows(IllegalArgumentException.class, () -> invalidName.createSql(target, SqlType.MERGE_TABLE));
		}
	}
}
