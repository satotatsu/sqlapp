/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlite.
 */
package com.sqlapp.data.db.dialect.sqlite.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;

/**
 * SQLite keeps a view whose table was dropped, but cannot report its columns.
 * One such view must not prevent reading the rest of the schema.
 */
class SqliteInvalidViewTest {
	@Test
	void invalidViewIsMarkedWithoutLosingOtherViews() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(a INTEGER, b TEXT)");
			statement.execute("CREATE TABLE gone(x INTEGER)");
			statement.execute("CREATE VIEW v_ok AS SELECT a, b FROM t");
			statement.execute("CREATE VIEW v_bad AS SELECT x FROM gone");
			statement.execute("CREATE VIEW v_ok2 AS SELECT a FROM t");
			statement.execute("DROP TABLE gone");
			var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader();
			reader.setSchemaName("main");
			var schema = reader.getAllFull(connection).get(0);
			var ok = schema.getViews().get("v_ok");
			assertEquals(List.of("a", "b"), ok.getColumns().stream().map(Column::getName).toList());
			assertNull(ok.getSpecifics().get(SqliteViewReader.INVALID_VIEW));
			assertEquals(1, schema.getViews().get("v_ok2").getColumns().size());
			assertEquals(1, schema.getTables().size());
			var bad = schema.getViews().get("v_bad");
			assertEquals("true", bad.getSpecifics().get(SqliteViewReader.INVALID_VIEW));
			assertTrue(bad.getColumns().isEmpty());
			// The retained statement still recreates the view as SQLite stored it.
			statement.execute("DROP VIEW v_bad");
			for (var operation : DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(bad, SqlType.CREATE))
				statement.execute(operation.getSqlText());
			statement.execute("CREATE TABLE gone(x INTEGER)");
			try (var rs = statement.executeQuery("SELECT x FROM v_bad")) {
				assertFalse(rs.next());
			}
		}
	}
}
