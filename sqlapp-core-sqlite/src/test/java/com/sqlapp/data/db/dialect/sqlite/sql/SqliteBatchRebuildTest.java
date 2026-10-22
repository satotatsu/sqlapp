/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Index;

class SqliteBatchRebuildTest {
	@Test
	void inferredViewColumnsAreValidatedWithNewAndRemovedTriggers() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(id INTEGER PRIMARY KEY,value INT)"); statement.execute("INSERT INTO t VALUES(40,7)");
			statement.execute("CREATE VIEW obsolete AS SELECT value FROM t");
			statement.execute("CREATE TRIGGER old AFTER INSERT ON t BEGIN SELECT new.id; END");
			var desired = read(connection, "main"); desired.getViews().clear(); desired.getTriggers().clear();
			var view = new com.sqlapp.data.schemas.View("fresh"); view.setStatement("SELECT value AS amount FROM t"); desired.getViews().add(view);
			var trigger = new com.sqlapp.data.schemas.Trigger("write_view").setTableName("fresh").setTableSchemaName("main");
			trigger.setDefinition("CREATE TRIGGER write_view INSTEAD OF UPDATE OF amount ON fresh BEGIN UPDATE t SET value=new.amount; END"); desired.getTriggers().add(trigger);
			String xml = desired.asXml();
			new SqliteTableRebuilder().rebuild(connection, desired);
			assertEquals(xml, desired.asXml());
			assertThrows(SQLException.class, () -> statement.executeQuery("SELECT * FROM obsolete"));
			statement.execute("UPDATE fresh SET amount=9");
			try (var rows = statement.executeQuery("SELECT value FROM t")) { assertTrue(rows.next()); assertEquals(9, rows.getInt(1)); }
			var broken = desired.clone(); broken.getTriggers().get("write_view").setDefinition("CREATE TRIGGER write_view INSTEAD OF UPDATE ON fresh BEGIN SELECT new.absent; END");
			var before = catalog(connection, "main");
			assertThrows(SQLException.class, () -> new SqliteTableRebuilder().rebuild(connection, broken));
			assertEquals(before, catalog(connection, "main"));
		}
	}

	@Test
	void reconstructsMutuallyReferencingTablesWithOneCommit() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = connection.createStatement()) {
			statement.execute("PRAGMA foreign_keys=ON");
			statement.execute("CREATE TABLE a(id INTEGER PRIMARY KEY,b INT REFERENCES b(id) DEFERRABLE INITIALLY DEFERRED)");
			statement.execute("CREATE TABLE b(id INTEGER PRIMARY KEY,a INT REFERENCES a(id) DEFERRABLE INITIALLY DEFERRED)");
			connection.setAutoCommit(false); statement.execute("INSERT INTO a VALUES(1,2)"); statement.execute("INSERT INTO b VALUES(2,1)"); connection.commit(); connection.setAutoCommit(true);
			assertEquals(Map.of("a",1L,"b",1L), new SqliteTableRebuilder().rebuild(connection, read(connection,"main")).rowCounts());
			try (var rows = statement.executeQuery("PRAGMA foreign_key_check")) { assertFalse(rows.next()); }
			assertTrue(connection.getAutoCommit());
		}
	}

	@Test
	void rejectsInvalidSelectionAndOptionsWithoutChangingTheDatabase() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(id INTEGER PRIMARY KEY)"); statement.execute("INSERT INTO t VALUES(40)");
			var desired = read(connection,"main"); var before = catalog(connection,"main");
			for (var options : List.of(new SqliteTableRebuilder.Options().tables("missing"), new SqliteTableRebuilder.Options().tables("t","T"),
					new SqliteTableRebuilder.Options().sourceColumns("missing", Map.of()),
					new SqliteTableRebuilder.Options().rowIdentity("missing",SqliteTableRebuilder.RowIdentity.REGENERATE),
					new SqliteTableRebuilder.Options().temporaryDependencies(new Schema("main")),
					new SqliteTableRebuilder.Options().sourceColumns("t",Map.of()).sourceColumns("T",Map.of()))) {
				assertThrows(IllegalArgumentException.class, () -> new SqliteTableRebuilder().rebuild(connection, desired, options));
				assertEquals(before,catalog(connection,"main")); assertTrue(connection.getAutoCommit());
			}
		}
	}
	static Stream<Arguments> cases() {
		return Stream.of("main", "temp", "other\" db").flatMap(database ->
				Stream.of("success", "copy", "index", "view", "trigger", "fk").map(outcome -> Arguments.of(database, outcome)));
	}

	@ParameterizedTest
	@MethodSource("cases")
	void replacesParentChildAndDependenciesAtomically(String database, String outcome) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = connection.createStatement()) {
			statement.execute("PRAGMA foreign_keys=ON"); statement.execute("ATTACH ':memory:' AS \"other\"\" db\"");
			String prefix = quote(database) + ".";
			statement.execute("CREATE TABLE " + prefix + "parent(id INTEGER PRIMARY KEY AUTOINCREMENT,value TEXT)");
			statement.execute("CREATE TABLE " + prefix + "child(pid INT REFERENCES parent(id) ON UPDATE CASCADE ON DELETE CASCADE)");
			statement.execute("CREATE TABLE " + prefix + "audit(value INT)");
			statement.execute("CREATE INDEX " + prefix + "collision ON audit(value)");
			statement.execute("INSERT INTO " + prefix + "parent VALUES(10,'old'),(1000,'deleted')");
			statement.execute("DELETE FROM " + prefix + "parent WHERE id=1000");
			statement.execute("INSERT INTO " + prefix + "child VALUES(10),(10)");
			statement.execute("CREATE VIEW " + prefix + "v AS SELECT id,value FROM parent");
			statement.execute("CREATE TRIGGER " + prefix + "tr AFTER INSERT ON parent BEGIN INSERT INTO audit VALUES(new.id); END");
			if (!database.equals("temp")) {
				statement.execute("CREATE TEMP VIEW tv AS SELECT id FROM " + prefix + "parent");
				statement.execute("CREATE TEMP TRIGGER tt AFTER UPDATE OF id ON " + prefix + "parent BEGIN SELECT new.id; END");
			}
			var before = catalog(connection, database); var tempBefore = catalog(connection, "temp");
			var desired = read(connection, database);
			var parent = desired.getTables().get("parent"); var id = parent.getColumns().get("id"); id.setName("new id");
			parent.getPrimaryKeyConstraint().getColumns().clear(); parent.getPrimaryKeyConstraint().getColumns().add(id);
			var child = desired.getTables().get("child"); var key = child.getConstraints().getForeignKeyConstraints().get(0);
			key.getRelatedColumns().clear(); key.getRelatedColumns().add(new Column(outcome.equals("fk") ? "absent" : "new id").setTableName("parent").setSchemaName(database));
			desired.getViews().get("v").setStatement("SELECT \"new id\" AS id,value FROM parent");
			desired.getTriggers().get("tr").setDefinition("CREATE TRIGGER tr AFTER INSERT ON parent BEGIN INSERT INTO audit VALUES(new.\"new id\"); END");
			if (outcome.equals("view")) desired.getViews().get("v").setStatement("SELECT absent FROM parent");
			if (outcome.equals("trigger")) desired.getTriggers().get("tr").setDefinition("CREATE TRIGGER tr AFTER INSERT ON parent BEGIN SELECT new.absent; END");
			if (outcome.equals("copy")) child.getConstraints().add(new CheckConstraint("reject", "pid<0"));
			if (outcome.equals("index")) { var index = new Index("collision"); index.getColumns().add(id); parent.getIndexes().add(index); }
			var options = new SqliteTableRebuilder.Options().tables("parent", "child").sourceColumns("parent", Map.of("new id", "id"));
			if (!database.equals("temp")) {
				var temporary = read(connection, "temp");
				temporary.getViews().get("tv").setStatement("SELECT \"new id\" AS id FROM " + prefix + "parent");
				temporary.getTriggers().get("tt").setDefinition("CREATE TEMP TRIGGER tt AFTER UPDATE OF \"new id\" ON " + prefix + "parent BEGIN SELECT new.\"new id\"; END");
				options.temporaryDependencies(temporary);
			}
			String xml = desired.asXml();
			if (outcome.equals("success")) {
				var result = new SqliteTableRebuilder().rebuild(connection, desired, options);
				assertEquals(Map.of("parent", 1L, "child", 2L), result.rowCounts());
				assertThrows(UnsupportedOperationException.class, () -> result.rowCounts().clear());
				assertThrows(UnsupportedOperationException.class, () -> result.operations().clear());
			} else {
				assertThrows(SQLException.class, () -> new SqliteTableRebuilder().rebuild(connection, desired, options));
				assertEquals(before, catalog(connection, database)); assertEquals(tempBefore, catalog(connection, "temp"));
			}
			assertEquals(xml, desired.asXml()); assertTrue(connection.getAutoCommit());
			try (var rows = statement.executeQuery("SELECT count(*) FROM " + prefix + "audit")) { assertTrue(rows.next()); assertEquals(0, rows.getInt(1)); }
			try (var rows = statement.executeQuery("SELECT id,value FROM " + prefix + "v")) { assertTrue(rows.next()); assertEquals(10, rows.getInt(1)); assertEquals("old", rows.getString(2)); }
			String idName = outcome.equals("success") ? "\"new id\"" : "id";
			statement.execute("UPDATE " + prefix + "parent SET " + idName + "=20 WHERE " + idName + "=10");
			try (var rows = statement.executeQuery("SELECT min(pid),count(*) FROM " + prefix + "child")) { assertTrue(rows.next()); assertEquals(20, rows.getInt(1)); assertEquals(2, rows.getInt(2)); }
			statement.execute("INSERT INTO " + prefix + "parent(value) VALUES('next')");
			try (var rows = statement.executeQuery("SELECT value FROM " + prefix + "audit")) { assertTrue(rows.next()); assertEquals(1001, rows.getInt(1)); assertFalse(rows.next()); }
			statement.execute("DELETE FROM " + prefix + "parent WHERE " + idName + "=20");
			try (var rows = statement.executeQuery("SELECT count(*) FROM " + prefix + "child")) { assertTrue(rows.next()); assertEquals(0, rows.getInt(1)); }
		}
	}

	static Schema read(Connection connection, String database) {
		var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader(); reader.setSchemaName(database); return reader.getAllFull(connection).get(0);
	}
	static List<String> catalog(Connection connection, String database) throws SQLException {
		var result = new ArrayList<String>();
		try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT name,sql FROM " + quote(database) + ".sqlite_master ORDER BY name")) {
			while (rows.next()) result.add(rows.getString(1) + "|" + rows.getString(2));
		}
		return result;
	}
	static String quote(String name) { return "\"" + name.replace("\"", "\"\"") + "\""; }
}
