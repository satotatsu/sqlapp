/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Files;
import java.sql.DriverManager;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.schemas.loader.SchemaFileLoaderResolver;
import com.sqlapp.data.schemas.ForeignKeyConstraint;

class SqliteFileLoaderTest {
	@TempDir
	Path tempDirectory;

	@Test
	void loadsMetadataAndRowsThroughExistingMetadataReader() throws Exception {
		final Path file = tempDirectory.resolve("日本語.sqlite3");
		try (var connection = DriverManager
				.getConnection("jdbc:sqlite:" + file);
				var statement = connection.createStatement()) {
			statement.execute("PRAGMA foreign_keys = ON");
			statement.execute("CREATE TABLE \"部署\" (\"部署ID\" INTEGER PRIMARY KEY, \"名称\" TEXT NOT NULL)");
			statement.execute("CREATE TABLE \"社員\" (\"社員ID\" INTEGER PRIMARY KEY AUTOINCREMENT, \"氏名\" TEXT NOT NULL DEFAULT '未設定', \"部署ID\" INTEGER, CONSTRAINT \"FK_社員_部署\" FOREIGN KEY (\"部署ID\") REFERENCES \"部署\" (\"部署ID\"), CHECK (length(\"氏名\") > 0))");
			statement.execute("CREATE INDEX \"IDX_社員_氏名\" ON \"社員\" (\"氏名\")");
			statement.execute("CREATE VIEW \"社員一覧\" AS SELECT \"社員ID\", \"氏名\" FROM \"社員\"");
			statement.execute("CREATE TRIGGER \"TRG_社員\" AFTER UPDATE ON \"社員\" BEGIN SELECT 1; END");
			statement.execute("INSERT INTO \"部署\" VALUES (1, '開発')");
			statement.execute("INSERT INTO \"社員\" (\"氏名\", \"部署ID\") VALUES ('山田', 1)");
		}

		final var schema = SqliteFileLoader.loadSchema(file);
		assertEquals("main", schema.getName());
		final var table = schema.getTables().get("社員");
		assertNotNull(table);
		assertNotNull(table.getColumns().get("氏名"));
		assertNotNull(table.getIndexes().get("IDX_社員_氏名"));
		assertTrue(table.getConstraints().stream()
				.anyMatch(c -> c instanceof ForeignKeyConstraint fk
						&& "部署".equals(fk.getRelatedTableName())));
		assertNotNull(schema.getViews().get("社員一覧"));
		assertNotNull(schema.getTriggers().get("TRG_社員"));
		final var rows = table.getRows().iterator();
		assertTrue(rows.hasNext());
		assertEquals("山田", rows.next().get("氏名"));
		assertFalse(rows.hasNext());

		assertEquals("社員", SqliteFileLoader.loadTable(file, "社員")
				.getName());
		assertThrows(IllegalArgumentException.class,
				() -> SqliteFileLoader.loadTable(file, "不存在"));
	}

	@Test
	void resolvesCommonSqliteExtensions() throws Exception {
		for (final String extension : new String[] { ".db", ".sqlite",
				".sqlite3" }) {
			final Path file = tempDirectory.resolve("sample" + extension);
			try (var connection = DriverManager
					.getConnection("jdbc:sqlite:" + file)) {
				// Opening the connection creates a valid empty SQLite file.
			}
			assertTrue(SchemaFileLoaderResolver.resolve(file)
					instanceof SqliteSchemaFileLoader);
		}
	}

	@Test
	void resolvesAValidDatabaseWithAnArbitraryExtension() throws Exception {
		final Path file = tempDirectory.resolve("database.data");
		try (var connection = DriverManager
				.getConnection("jdbc:sqlite:" + file);
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE sample (id INTEGER)");
		}
		assertTrue(SchemaFileLoaderResolver.resolve(file)
				instanceof SqliteSchemaFileLoader);
	}

	@Test
	void loadsASelectedAttachedDatabaseAndItsRows() throws Exception {
		final Path primary = tempDirectory.resolve("primary.db");
		final Path attached = tempDirectory.resolve("archive.db");
		try (var connection = DriverManager
				.getConnection("jdbc:sqlite:" + primary)) {
			// Create the primary file.
		}
		try (var connection = DriverManager
				.getConnection("jdbc:sqlite:" + attached);
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE history (id INTEGER PRIMARY KEY, value TEXT)");
			statement.execute("INSERT INTO history VALUES (1, 'archived')");
		}

		final var schema = SqliteFileLoader.loadSchema(primary, "archive",
				Map.of("archive", attached));
		assertEquals("archive", schema.getName());
		final var rows = schema.getTables().get("history").getRows().iterator();
		assertTrue(rows.hasNext());
		assertEquals("archived", rows.next().get("value"));
		assertFalse(rows.hasNext());
		assertEquals("history", SqliteFileLoader.loadTable(primary,
				"archive", Map.of("archive", attached), "history").getName());
	}

	@Test
	void reportsEncryptedOrInvalidFilesClearly() throws Exception {
		final Path file = tempDirectory.resolve("encrypted.db");
		Files.writeString(file, "not a plain SQLite database");
		final var exception = assertThrows(java.io.IOException.class,
				() -> SqliteFileLoader.loadSchema(file));
		assertTrue(exception.getMessage().contains("may be encrypted"));
	}
}
