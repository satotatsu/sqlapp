/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlite.
 */
package com.sqlapp.data.db.dialect.sqlite.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.dialect.sqlite.util.SqliteDdl;
import com.sqlapp.data.db.sql.SqlType;

/**
 * SQLite accepts AUTOINCREMENT inside a table-level PRIMARY KEY constraint, the
 * form written by common SQLite GUI tools.
 */
class SqliteTableLevelAutoincrementTest {
	private static final String DDL = "CREATE TABLE \"t\" (\"id\" INTEGER, \"name\" TEXT, PRIMARY KEY(\"id\" AUTOINCREMENT))";

	@Test
	void parsesTableLevelAutoincrementKey() {
		var keys = SqliteDdl.keys("PRIMARY KEY(\"id\" ASC AUTOINCREMENT)");
		assertEquals(1, keys.size());
		assertTrue(keys.get(0).primary());
		assertEquals(1, keys.get(0).columns().size());
		assertEquals("id", keys.get(0).columns().get(0).name());
		assertFalse(keys.get(0).columns().get(0).descending());
		assertTrue(SqliteDdl.isTableLevelAutoIncrement("PRIMARY KEY(\"id\" AUTOINCREMENT)", "id"));
		assertTrue(SqliteDdl.isTableLevelAutoIncrement("CONSTRAINT pk PRIMARY KEY([id] ASC AUTOINCREMENT)", "id"));
		assertFalse(SqliteDdl.isTableLevelAutoIncrement("PRIMARY KEY(\"id\")", "id"));
		assertFalse(SqliteDdl.isTableLevelAutoIncrement("PRIMARY KEY(\"id\" AUTOINCREMENT)", "name"));
		assertFalse(SqliteDdl.isTableLevelAutoIncrement("\"id\" INTEGER PRIMARY KEY AUTOINCREMENT", "id"));
	}

	@Test
	void readsAndRecreatesTableLevelAutoincrement() throws Exception {
		var dialect = DialectHolder.defaultDialect;
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute(DDL);
			var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setSchemaName("main");
			reader.setObjectName("t");
			var table = reader.getAllFull(connection).get(0);
			var id = table.getColumns().get("id");
			assertTrue(id.isIdentity());
			assertEquals("true", id.getSpecifics().get("sqlite.autoincrement"));
			var primary = table.getConstraints().getPrimaryKeyConstraint();
			assertEquals(1, primary.getColumns().size());
			assertEquals("id", primary.getColumns().get(0).getName());
			statement.execute("DROP TABLE \"t\"");
			for (var operation : dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE))
				statement.execute(operation.getSqlText());
			statement.execute("INSERT INTO \"t\"(\"name\") VALUES ('a')");
			// Only an AUTOINCREMENT table registers itself in sqlite_sequence.
			try (var rs = statement.executeQuery("SELECT seq FROM sqlite_sequence WHERE name='t'")) {
				assertTrue(rs.next());
				assertEquals(1, rs.getLong(1));
			}
		}
	}

	@Test
	void legacyMetadataPathReadsTableLevelAutoincrement() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute(DDL);
			var context = new com.sqlapp.data.parameter.ParametersContext();
			context.put("schemaName", "main");
			context.put("tableName", "t");
			var version = new com.sqlapp.data.schemas.ProductVersionInfo().setMajorVersion(3).setMinorVersion(15);
			var id = new SqliteColumnReader(DialectHolder.defaultDialect).doGetAll(connection, context, version).get(0);
			assertTrue(id.isIdentity());
			assertEquals("true", id.getSpecifics().get("sqlite.autoincrement"));
		}
	}
}
