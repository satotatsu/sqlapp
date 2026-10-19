/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;

class SqliteTemporaryTableLifecycleTest {
	private Table read(Connection connection, String schema, String name) {
		var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setSchemaName(schema);
		reader.setObjectName(name);
		return reader.getAllFull(connection).get(0);
	}

	private void execute(Statement statement, List<SqlOperation> operations) throws Exception {
		assertFalse(operations.isEmpty());
		for (var operation : operations)
			statement.execute(operation.getSqlText());
	}

	@Test
	void temporaryCreationComposesColumnConstraintsIndexesAndLeavesSourceUnchanged() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("""
					CREATE TABLE source(id INTEGER PRIMARY KEY AUTOINCREMENT,
					 value TEXT NOT NULL COLLATE NOCASE,
					 derived TEXT AS (lower(value)) STORED UNIQUE, CHECK(length(value)>0))
					""");
			statement.execute("CREATE INDEX ix ON source(value) WHERE id>0");
			statement.execute("INSERT INTO source(value) VALUES ('main')");
			var schemaReader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader();
			schemaReader.setSchemaName("main");
			var source = schemaReader.getAllFull(connection).get(0).getTables().get("source");
			String original = source.asXml();
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			registry.getTableOptions().setTemporaryTableNamePrefix(t -> "t_");
			registry.getTableOptions().setTemporaryTableNameSuffix(t -> "_copy");
			var operations = registry.createSql(source, SqlType.CREATE_TEMPORARY);
			assertEquals(SqlType.CREATE_TEMPORARY, operations.get(0).getSqlType());
			assertSame(source, operations.get(0).getOriginal());
			assertEquals("temp", operations.get(0).<Table>getTarget().getSchemaName());
			assertEquals(original, source.asXml());
			execute(statement, operations);
			statement.execute("INSERT INTO temp.t_source_copy(value) VALUES ('HELLO')");
			assertThrows(SQLException.class,
					() -> statement.execute("INSERT INTO temp.t_source_copy(value) VALUES ('hello')"));
			assertThrows(SQLException.class,
					() -> statement.execute("INSERT INTO temp.t_source_copy(value) VALUES ('')"));
			assertEquals("lower(value)",
					read(connection, "temp", "t_source_copy").getColumns().get("derived").getFormula());
			assertNotNull(read(connection, "temp", "t_source_copy").getIndexes().get("t_ix_copy"));
			statement.execute("CREATE TABLE main.t_source_copy(value TEXT)");
			statement.execute("INSERT INTO main.t_source_copy VALUES ('untouched')");
			execute(statement, registry.createSql(source, SqlType.TRUNCATE_TEMPORARY));
			try (var rs = statement.executeQuery("SELECT count(*) FROM temp.t_source_copy")) {
				assertTrue(rs.next());
				assertEquals(0, rs.getInt(1));
			}
			statement.execute("DROP TABLE temp.t_source_copy");
			assertThrows(SQLException.class, () -> {
				for (var operation : registry.createSql(source, SqlType.TRUNCATE_TEMPORARY))
					statement.execute(operation.getSqlText());
			});
			try (var rs = statement.executeQuery("SELECT value FROM main.t_source_copy")) {
				assertTrue(rs.next());
				assertEquals("untouched", rs.getString(1));
			}
		}
	}

	@Test
	void temporaryOptionsAndSelfReferencesUseTheTemporaryIdentity() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("PRAGMA foreign_keys=ON");
			statement.execute(
					"CREATE TABLE tree(id INT PRIMARY KEY, parent INT REFERENCES tree(id)) WITHOUT ROWID, STRICT");
			var source = read(connection, "main", "tree");
			String original = source.asXml();
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			registry.getTableOptions().setTemporaryTableNamePrefix(t -> "tmp_");
			execute(statement, registry.createSql(source, SqlType.CREATE_TEMPORARY));
			assertEquals(original, source.asXml());
			statement.execute("INSERT INTO temp.tmp_tree VALUES (1,NULL)");
			statement.execute("INSERT INTO temp.tmp_tree VALUES (2,1)");
			assertThrows(SQLException.class, () -> statement.execute("INSERT INTO temp.tmp_tree VALUES (3,99)"));
			assertThrows(SQLException.class, () -> statement.execute("INSERT INTO temp.tmp_tree VALUES ('bad',NULL)"));
			assertThrows(SQLException.class, () -> statement.execute("SELECT rowid FROM temp.tmp_tree"));
		}
	}

	@Test
	void foreignKeysToMainCannotSilentlyRebindToTemporaryParents() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("PRAGMA foreign_keys=ON");
			statement.execute("CREATE TABLE parent(id INTEGER PRIMARY KEY)");
			statement.execute("CREATE TABLE child(id INT REFERENCES parent(id))");
			var source = read(connection, "main", "child");
			var error = assertThrows(IllegalArgumentException.class, () -> DialectHolder.defaultDialect
					.createSqlFactoryRegistry().createSql(source, SqlType.CREATE_TEMPORARY));
			assertTrue(error.getMessage().contains("explicit temp parent"));
			statement.execute("CREATE TEMP TABLE parent(id INTEGER PRIMARY KEY)");
			var key = source.getConstraints().getForeignKeyConstraints().get(0);
			key.getRelatedColumns().clear();
			key.setRelatedTableSchemaName("temp");
			var parentModel = new Table("parent").setSchemaName("temp");
			parentModel.getColumns().add(new com.sqlapp.data.schemas.Column("id"));
			key.getRelatedColumns().addAll(parentModel.getColumns().get("id"));
			execute(statement, DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(source,
					SqlType.CREATE_TEMPORARY));
			assertThrows(SQLException.class, () -> statement.execute("INSERT INTO temp.child VALUES (1)"));
			statement.execute("INSERT INTO temp.parent VALUES (1)");
			statement.execute("INSERT INTO temp.child VALUES (1)");
		}
	}

	@Test
	void ordinaryTruncateTargetsAttachedDatabaseDespiteTemporaryShadow() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS other");
			for (String schema : List.of("main", "other", "temp")) {
				statement.execute("CREATE TABLE " + schema + ".t(value TEXT)");
				statement.execute("INSERT INTO " + schema + ".t VALUES ('kept')");
			}
			execute(statement, DialectHolder.defaultDialect.createSqlFactoryRegistry()
					.createSql(new Table("t").setSchemaName("other"), SqlType.TRUNCATE));
			for (String schema : List.of("main", "other", "temp")) {
				try (var rs = statement.executeQuery("SELECT count(*) FROM " + schema + ".t")) {
					assertTrue(rs.next());
					assertEquals(schema.equals("other") ? 0 : 1, rs.getInt(1));
				}
			}
		}
	}
}
