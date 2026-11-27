/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.sqlite.Collation;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

class SqliteReindexFactoryTest {
	@Test
	void expressionRebuildRepairsChangedFunctionKeysOnlyInTheSelectedSchema() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			var offset = new java.util.concurrent.atomic.AtomicInteger();
			org.sqlite.Function.create(connection, "shifted", new org.sqlite.Function() {
				@Override
				protected void xFunc() throws SQLException {
					result(value_int(0) + offset.get());
				}
			}, 1, org.sqlite.Function.FLAG_DETERMINISTIC);
			statement.execute("ATTACH ':memory:' AS other");
			for (String name : new String[] { "main", "other" }) {
				statement.execute("CREATE TABLE " + name + ".t(value INT)");
				statement.execute("CREATE INDEX " + name + ".expression_idx ON t(shifted(value))");
				statement.execute("INSERT INTO " + name + ".t VALUES(1),(2)");
			}
			var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setSchemaName("other");
			var schema = new Schema("other");
			reader.getAllFull(connection).forEach(schema.getTables()::add);
			offset.set(100);
			var factory = (SqliteReindexFactory) DialectHolder.defaultDialect.createSqlFactoryRegistry()
					.getSqlFactory(schema, SqlType.REBUILD);
			for (var operation : factory.createExpressionIndexSql(schema))
				statement.execute(operation.getSqlText());
			for (String name : new String[] { "main", "other" }) {
				try (var result = statement.executeQuery("SELECT shifted(value) FROM " + name
						+ ".t INDEXED BY expression_idx ORDER BY shifted(value)")) {
					assertTrue(result.next());
					assertEquals(name.equals("other") ? 101 : 1, result.getInt(1));
					assertTrue(result.next());
					assertEquals(name.equals("other") ? 102 : 2, result.getInt(1));
					assertFalse(result.next());
				}
			}
		}
	}

	@Test
	void schemaExpressionRebuildUsesOnlyModeledExpressionIndexes() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS other");
			for (String name : new String[] { "main", "other" }) {
				statement.execute("CREATE TABLE " + name + ".t(value TEXT)");
				statement.execute("CREATE INDEX " + name + ".plain_idx ON t(value)");
				statement
						.execute("CREATE INDEX " + name + ".expression_idx ON t(lower(value)) WHERE value IS NOT NULL");
				statement.execute("INSERT INTO " + name + ".t VALUES('Kept')");
			}
			var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setSchemaName("other");
			var schema = new Schema("other");
			reader.getAllFull(connection).forEach(schema.getTables()::add);
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			var factory = (SqliteReindexFactory) registry.getSqlFactory(schema, SqlType.REBUILD);
			var operations = factory.createExpressionIndexSql(schema);
			assertEquals(1, operations.size());
			assertTrue(operations.get(0).getSqlText().contains("expression_idx"));
			assertTrue(operations.get(0).getSqlText().contains("other"));
			assertFalse(operations.get(0).getSqlText().contains("plain_idx"));
			statement.execute(operations.get(0).getSqlText());
			try (var result = statement.executeQuery(
					"SELECT value FROM other.t INDEXED BY expression_idx WHERE lower(value)='kept' AND value IS NOT NULL")) {
				assertTrue(result.next());
				assertEquals("Kept", result.getString(1));
			}
		}
	}

	@Test
	void expressionRebuildRejectsOldTargetsAndAcceptsAnEmptySelection() {
		var schema = new Schema("main");
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		var factory = (SqliteReindexFactory) registry.getSqlFactory(schema, SqlType.REBUILD);
		assertTrue(factory.createExpressionIndexSql(schema).isEmpty());
		assertThrows(IllegalArgumentException.class, () -> factory.createExpressionIndexSql(new Schema("")));
		var old = DialectResolver.getInstance().getDialect("SQLite", 3, 8, 8).createSqlFactoryRegistry();
		var oldFactory = (SqliteReindexFactory) old.getSqlFactory(schema, SqlType.REBUILD);
		assertThrows(IllegalArgumentException.class, () -> oldFactory.createExpressionIndexSql(schema));
	}

	@Test
	void rebuildsOnlyTheQualifiedIndexThenAllIndexesOfTheSelectedTable() throws Exception {
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			var reverse = new AtomicBoolean();
			Collation.create(connection, "changed", new Collation() {
				@Override
				protected int xCompare(String left, String right) {
					return reverse.get() ? right.compareTo(left) : left.compareTo(right);
				}
			});
			statement.execute("ATTACH ':memory:' AS \"Other]Db\"");
			for (String schema : new String[] { "main", "\"Other]Db\"" }) {
				statement.execute("CREATE TABLE " + schema + ".t(value TEXT COLLATE changed)");
				statement.execute("CREATE INDEX " + schema + ".idx ON t(value)");
				statement.execute("CREATE INDEX " + schema + ".idx2 ON t(value)");
				statement.execute("INSERT INTO " + schema + ".t VALUES('a'),('b')");
			}
			reverse.set(true);
			var operation = registry.createSql(new Index("idx").setSchemaName("Other]Db"), SqlType.REBUILD).get(0);
			assertEquals(SqlType.REBUILD, operation.getSqlType());
			statement.execute(operation.getSqlText());
			for (String[] target : new String[][] { { "main", "idx", "ab" }, { "\"Other]Db\"", "idx", "ba" },
					{ "\"Other]Db\"", "idx2", "ab" } }) {
				try (var result = statement.executeQuery(
						"SELECT value FROM " + target[0] + ".t INDEXED BY " + target[1] + " ORDER BY value")) {
					String values = "";
					while (result.next())
						values += result.getString(1);
					assertEquals(target[2], values);
				}
			}
			statement.execute(
					registry.createSql(new Table("t").setSchemaName("Other]Db"), SqlType.REBUILD).get(0).getSqlText());
			try (var result = statement
					.executeQuery("SELECT value FROM \"Other]Db\".t INDEXED BY idx2 ORDER BY value")) {
				assertTrue(result.next());
				assertEquals("b", result.getString(1));
				assertTrue(result.next());
				assertEquals("a", result.getString(1));
				assertFalse(result.next());
			}
		}
	}

	@Test
	void rebuildingInsideATransactionKeepsTheCallerInControlAndReportsMissingObjects() throws Exception {
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TEMP TABLE \"odd' ] table\"(value INT UNIQUE)");
			connection.setAutoCommit(false);
			statement.execute("INSERT INTO temp.\"odd' ] table\" VALUES(7)");
			statement.execute(registry.createSql(new Table("odd' ] table").setSchemaName("temp"), SqlType.REBUILD)
					.get(0).getSqlText());
			assertFalse(connection.getAutoCommit());
			assertThrows(SQLException.class, () -> statement.execute(registry
					.createSql(new Index("missing").setSchemaName("temp"), SqlType.REBUILD).get(0).getSqlText()));
			connection.rollback();
			try (var result = statement.executeQuery("SELECT count(*) FROM temp.\"odd' ] table\"")) {
				assertTrue(result.next());
				assertEquals(0, result.getInt(1));
			}
		}
	}

	@Test
	void schemaQualifiedNamesAvoidCollationPrecedenceAndInjection() throws Exception {
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE nocase(value TEXT UNIQUE)");
			statement.execute(
					registry.createSql(new Table("nocase").setSchemaName("main"), SqlType.REBUILD).get(0).getSqlText());
			String sql = registry
					.createSql(new Index("missing; DROP TABLE nocase; --").setSchemaName("main"), SqlType.REBUILD)
					.get(0).getSqlText();
			assertThrows(SQLException.class, () -> statement.execute(sql));
			try (var result = statement.executeQuery("SELECT count(*) FROM nocase")) {
				assertTrue(result.next());
			}
		}
	}

	@Test
	void uniqueIndexRebuildFailuresRemainVisible() throws Exception {
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			var foldCase = new AtomicBoolean();
			Collation.create(connection, "changed", new Collation() {
				@Override
				protected int xCompare(String left, String right) {
					return foldCase.get() ? left.compareToIgnoreCase(right) : left.compareTo(right);
				}
			});
			statement.execute("CREATE TABLE t(value TEXT COLLATE changed)");
			statement.execute("CREATE UNIQUE INDEX idx ON t(value)");
			statement.execute("INSERT INTO t VALUES('A'),('a')");
			foldCase.set(true);
			String sql = registry.createSql(new Index("idx").setSchemaName("main"), SqlType.REBUILD).get(0)
					.getSqlText();
			assertThrows(SQLException.class, () -> statement.execute(sql));
			try (var result = statement.executeQuery("SELECT count(*) FROM t NOT INDEXED")) {
				assertTrue(result.next());
				assertEquals(2, result.getInt(1));
			}
		}
	}

	@Test
	void rejectsAmbiguousTargetsAndRetainsLegacySyntax() {
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(new Table("t"), SqlType.REBUILD));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(new Index("idx"), SqlType.REBUILD));
		assertTrue(registry.createSql(new Schema("main"), SqlType.REBUILD).isEmpty());
		assertThrows(IllegalArgumentException.class,
				() -> registry.createSql(new Table("").setSchemaName("main"), SqlType.REBUILD));
		var target = new Index("idx").setSchemaName("main");
		var old = DialectResolver.getInstance().getDialect("SQLite", 3, 7, 0).createSqlFactoryRegistry();
		assertEquals(registry.createSql(target, SqlType.REBUILD).get(0).getSqlText(),
				old.createSql(target, SqlType.REBUILD).get(0).getSqlText());
	}
}
