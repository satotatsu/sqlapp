/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;
import static com.sqlapp.data.db.dialect.sqlite.sql.SqliteBatchRebuildTest.*;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.sqlapp.data.schemas.Schema;

class SqliteRebuildIdentityModesTest {
	static Stream<Arguments> modes() {
		return Stream.of("main", "temp", "other\" db").flatMap(database -> Stream.of(
				"without", "rowid", "add", "remove", "autoincrement", "removeAuto", "switch", "duplicate", "null", "dependency")
				.map(mode -> Arguments.of(database, mode)));
	}

	@ParameterizedTest
	@MethodSource("modes")
	void changesIdentityOnlyWhenExplicitAndRollsBackInvalidCopies(String database, String mode) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS \"other\"\" db\"");
			String prefix = quote(database) + ".";
			String source = switch (mode) {
				case "without", "dependency" -> "(key TEXT PRIMARY KEY,value TEXT)";
				case "rowid" -> "(key TEXT PRIMARY KEY,value TEXT) WITHOUT ROWID";
				case "add" -> "(value TEXT)";
				case "switch" -> "(id INTEGER PRIMARY KEY,code INT UNIQUE,value TEXT)";
				case "removeAuto" -> "(id INTEGER PRIMARY KEY AUTOINCREMENT,value TEXT)";
				case "duplicate", "null" -> "(key TEXT,value TEXT)";
				default -> "(id INTEGER PRIMARY KEY,value TEXT)";
			};
			String targetDdl = switch (mode) {
				case "without", "dependency", "duplicate", "null" -> "(key TEXT PRIMARY KEY,value TEXT) WITHOUT ROWID";
				case "rowid" -> "(key TEXT PRIMARY KEY,value TEXT)";
				case "add", "autoincrement" -> "(id INTEGER PRIMARY KEY AUTOINCREMENT,value TEXT)";
				case "remove" -> "(id INT PRIMARY KEY,value TEXT)";
				case "switch" -> "(id INT UNIQUE,code INTEGER PRIMARY KEY,value TEXT)";
				default -> "(id INTEGER PRIMARY KEY,value TEXT)";
			};
			statement.execute("CREATE TABLE " + prefix + "t" + source);
			String insert = switch (mode) {
				case "without", "dependency" -> "(rowid,key,value) VALUES(40,'a','old')";
				case "rowid" -> " VALUES('a','old')";
				case "add" -> "(rowid,value) VALUES(40,'old')";
				case "switch" -> " VALUES(40,80,'old')";
				case "duplicate" -> " VALUES('a','old'),('a','second')";
				case "null" -> " VALUES(NULL,'old')";
				default -> " VALUES(40,'old')";
			};
			statement.execute("INSERT INTO " + prefix + "t" + insert);
			if (mode.equals("removeAuto")) { statement.execute("INSERT INTO " + prefix + "t VALUES(1000,'deleted')"); statement.execute("DELETE FROM " + prefix + "t WHERE id=1000"); }
			if (mode.equals("dependency")) statement.execute("CREATE VIEW " + prefix + "v AS SELECT rowid FROM t");
			statement.execute("CREATE TABLE " + prefix + "template" + targetDdl);
			var target = read(connection, database).getTables().get("template").clone(); target.setName("t"); statement.execute("DROP TABLE " + prefix + "template");
			var desired = read(connection, database); desired.getTables().remove("t"); desired.getTables().add(target);
			var before = catalog(connection, database); String xml = desired.asXml();
			var options = new SqliteTableRebuilder.Options().rowIdentity("t", SqliteTableRebuilder.RowIdentity.REGENERATE);
			if (!mode.equals("autoincrement") && !mode.equals("removeAuto"))
				assertThrows(IllegalArgumentException.class, () -> new SqliteTableRebuilder().rebuild(connection, desired));
			boolean failure = mode.equals("duplicate") || mode.equals("null") || mode.equals("dependency");
			if (failure) {
				assertThrows(SQLException.class, () -> new SqliteTableRebuilder().rebuild(connection, desired, options));
				assertEquals(before, catalog(connection, database));
			} else {
				assertEquals(1L, new SqliteTableRebuilder().rebuild(connection, desired, options).rowCounts().get("t"));
				try (var rows = statement.executeQuery("SELECT value FROM " + prefix + "t")) { assertTrue(rows.next()); assertEquals("old", rows.getString(1)); assertFalse(rows.next()); }
				if (mode.equals("without")) assertThrows(SQLException.class, () -> statement.executeQuery("SELECT rowid FROM " + prefix + "t"));
				else {
					int expected = switch (mode) { case "rowid", "add", "remove" -> 1; case "switch" -> 80; default -> 40; };
					try (var rows = statement.executeQuery("SELECT rowid FROM " + prefix + "t")) { assertTrue(rows.next()); assertEquals(expected, rows.getInt(1)); }
				}
				if (mode.equals("remove") || mode.equals("switch"))
					try (var rows = statement.executeQuery("SELECT id FROM " + prefix + "t")) { assertTrue(rows.next()); assertEquals(40, rows.getInt(1)); }
				if (mode.equals("add") || mode.equals("autoincrement") || mode.equals("removeAuto")) {
					statement.execute("INSERT INTO " + prefix + "t(value) VALUES('next')");
					try (var rows = statement.executeQuery("SELECT id FROM " + prefix + "t WHERE value='next'")) { assertTrue(rows.next()); assertEquals(mode.equals("add") ? 2 : 41, rows.getInt(1)); }
				}
			}
			assertEquals(xml, desired.asXml()); assertTrue(connection.getAutoCommit());
		}
	}
}
