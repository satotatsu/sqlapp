/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.schemas.Schema;

class SqliteIntegrityCheckBuilderTest {
	@Test
	void defaultAndQuickChecksExecuteAgainstTheirSelectedSchema() throws Exception {
		var builder = new SqliteIntegrityCheckBuilder(DialectHolder.defaultDialect);
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS \"Other]Db\"");
			statement.execute("CREATE TABLE \"Other]Db\".t(id INTEGER PRIMARY KEY, value TEXT UNIQUE)");
			statement.execute("INSERT INTO \"Other]Db\".t VALUES(1,'kept')");
			for (String sql : new String[] { builder.build(), builder.quick().maxErrors(3).build(),
					builder.schema(new Schema("Other]Db")).build(), builder.integrity().build() }) {
				try (var result = statement.executeQuery(sql)) {
					assertTrue(result.next());
					assertEquals("ok", result.getString(1));
					assertFalse(result.next());
				}
			}
			try (var result = statement.executeQuery("SELECT value FROM \"Other]Db\".t")) {
				assertTrue(result.next());
				assertEquals("kept", result.getString(1));
				assertFalse(result.next());
			}
		}
	}

	@Test
	void reportsConstraintFailuresAndHonorsTheErrorLimitWithoutRepairingData() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(value INT CHECK(value>0))");
			statement.execute("PRAGMA ignore_check_constraints=ON");
			statement.execute("INSERT INTO t VALUES(-1),(-2),(-3)");
			statement.execute("PRAGMA ignore_check_constraints=OFF");
			var builder = new SqliteIntegrityCheckBuilder(DialectHolder.defaultDialect).maxErrors(2);
			for (String sql : new String[] { builder.build(), builder.quick().build() }) {
				var errors = new ArrayList<String>();
				try (var result = statement.executeQuery(sql)) {
					while (result.next())
						errors.add(result.getString(1));
				}
				assertEquals(2, errors.size());
				assertTrue(errors.stream().allMatch(error -> error.contains("CHECK constraint failed in t")));
			}
			try (var result = statement.executeQuery("SELECT count(*) FROM t WHERE value<0")) {
				assertTrue(result.next());
				assertEquals(3, result.getInt(1));
			}
		}
	}

	@Test
	void schemaSelectionIsIsolatedAndNamesCannotInjectCommands() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE kept(id INT)");
			statement.execute("ATTACH ':memory:' AS broken");
			statement.execute("CREATE TABLE broken.t(value INT CHECK(value>0))");
			statement.execute("PRAGMA ignore_check_constraints=ON");
			statement.execute("INSERT INTO broken.t VALUES(-1)");
			statement.execute("PRAGMA ignore_check_constraints=OFF");
			var builder = new SqliteIntegrityCheckBuilder(DialectHolder.defaultDialect);
			try (var result = statement.executeQuery(builder.build())) {
				assertTrue(result.next());
				assertEquals("ok", result.getString(1));
			}
			try (var result = statement.executeQuery(builder.schema("broken").build())) {
				assertTrue(result.next());
				assertTrue(result.getString(1).contains("CHECK constraint failed"));
			}
			String sql = builder.schema("missing; DROP TABLE kept; --").build();
			assertThrows(SQLException.class, () -> statement.executeQuery(sql));
			try (var result = statement.executeQuery("SELECT count(*) FROM kept")) {
				assertTrue(result.next());
				assertEquals(0, result.getInt(1));
			}
		}
	}

	@Test
	void validatesOptionsAndRetainsLegacySyntax() {
		var builder = new SqliteIntegrityCheckBuilder(DialectHolder.defaultDialect);
		assertThrows(IllegalArgumentException.class, () -> builder.schema(" "));
		assertThrows(IllegalArgumentException.class, () -> builder.schema((String) null));
		assertThrows(IllegalArgumentException.class, () -> builder.maxErrors(0));
		assertThrows(IllegalArgumentException.class, () -> builder.maxErrors(-1));
		assertThrows(NullPointerException.class, () -> new SqliteIntegrityCheckBuilder(null));
		assertThrows(IllegalArgumentException.class, () -> new SqliteIntegrityCheckBuilder(
				DialectResolver.getInstance().getDialect("PostgreSQL", 15, 0, 0)));
		var legacy = new SqliteIntegrityCheckBuilder(DialectResolver.getInstance().getDialect("SQLite", 3, 7, 0));
		assertEquals(builder.build(), legacy.build());
		assertEquals(builder.quick().maxErrors(10).build(), legacy.quick().maxErrors(10).build());
	}
}
