/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Table;

class SqliteRebuildTempDependenciesTest {
	private static final List<String> DATABASES = List.of("main", "temp", "other\" db");

	@ParameterizedTest
	@CsvSource({"main,success","temp,success","other\" db,success","main,view","temp,view","other\" db,view",
		"main,body","temp,body","other\" db,body","main,updateof","temp,updateof","other\" db,updateof",
		"main,index","temp,index","other\" db,index","main,copy","temp,copy","other\" db,copy"})
	void restoresCrossDatabaseTempBindingsAndRollsBackAllDependencies(String database, String outcome) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = connection.createStatement()) {
			statement.execute("PRAGMA foreign_keys=ON"); statement.execute("ATTACH ':memory:' AS " + quote(DATABASES.get(2)));
			for (String name : DATABASES) {
				String prefix = quote(name) + ".";
				statement.execute("CREATE TABLE " + prefix + "t(id INTEGER PRIMARY KEY AUTOINCREMENT,value TEXT,obsolete INT)");
				statement.execute("CREATE TABLE " + prefix + "other(value TEXT)");
				statement.execute("CREATE INDEX " + prefix + "collision ON other(value)");
				statement.execute("INSERT INTO " + prefix + "t VALUES(10,'" + name + "',1),(1000,'deleted',2)");
				statement.execute("DELETE FROM " + prefix + "t WHERE id=1000");
			}
			statement.execute("CREATE TEMP TABLE trace(db TEXT,value TEXT)");
			var selections = new ArrayList<String>();
			for (String name : DATABASES) {
				selections.add("SELECT '" + name + "' AS db,id,value FROM " + quote(name) + ".t");
				statement.execute("CREATE TEMP TRIGGER " + quote("track " + name) + " AFTER INSERT ON " + quote(name) + ".t BEGIN INSERT INTO trace VALUES('" + name + "',NEW.value); END");
				if (!name.equals(database)) statement.execute("CREATE TEMP TRIGGER " + quote("update " + name) + " AFTER UPDATE OF obsolete ON " + quote(name) + ".t BEGIN SELECT 1; END");
			}
			statement.execute("CREATE TEMP VIEW combined AS " + String.join(" UNION ALL ", selections));
			statement.execute("CREATE TEMP VIEW proxy AS SELECT id,value FROM temp.t");
			statement.execute("CREATE TEMP TRIGGER proxy_update INSTEAD OF UPDATE ON temp.proxy BEGIN UPDATE t SET value=NEW.value WHERE id=OLD.id; END");
			if (outcome.equals("view")) statement.execute("CREATE TEMP VIEW bad_view AS SELECT obsolete FROM " + quote(database) + ".t");
			if (outcome.equals("body")) statement.execute("CREATE TEMP TRIGGER bad_trigger AFTER INSERT ON " + quote(database) + ".t BEGIN INSERT INTO trace VALUES('bad',NEW.obsolete); END");
			if (outcome.equals("updateof")) statement.execute("CREATE TEMP TRIGGER bad_trigger AFTER UPDATE OF obsolete ON " + quote(database) + ".t BEGIN SELECT 1; END");
			var target = read(connection, database); target.getColumns().remove("obsolete");
			target.getColumns().add(new Column("note").setDataType(DataType.VARCHAR).setDataTypeName("TEXT").setDefaultValue("'added'"));
			if (outcome.equals("copy")) target.getConstraints().add(new CheckConstraint("reject_copy", "id<0"));
			if (outcome.equals("index")) { var index = new com.sqlapp.data.schemas.Index("collision"); index.getColumns().add(target.getColumns().get("value")); target.getIndexes().add(index); }
			String xml = target.asXml(); var before = temporaryDefinitions(connection);
			if (outcome.equals("success")) assertEquals(1, new SqliteTableRebuilder().rebuild(connection, target).rowCount());
			else { assertThrows(SQLException.class, () -> new SqliteTableRebuilder().rebuild(connection, target)); assertEquals(before, temporaryDefinitions(connection)); }
			assertEquals(xml, target.asXml()); assertTrue(connection.getAutoCommit());
			try (var rows = statement.executeQuery("PRAGMA foreign_keys")) { assertTrue(rows.next()); assertEquals(1, rows.getInt(1)); }
			try (var rows = statement.executeQuery("SELECT count(*) FROM temp.trace")) { assertTrue(rows.next()); assertEquals(0, rows.getInt(1)); }
			try (var rows = statement.executeQuery("SELECT count(*) FROM temp.combined")) { assertTrue(rows.next()); assertEquals(3, rows.getInt(1)); }
			statement.execute("UPDATE temp.proxy SET value='proxy' WHERE id=10");
			for (String name : DATABASES) {
				String prefix = quote(name) + ".";
				try (var rows = statement.executeQuery("SELECT value FROM " + prefix + "t WHERE id=10")) { assertTrue(rows.next()); assertEquals(name.equals("temp") ? "proxy" : name, rows.getString(1)); }
				statement.execute("INSERT INTO " + prefix + "t(value) VALUES('new:" + name + "')");
				try (var rows = statement.executeQuery("SELECT id FROM " + prefix + "t WHERE value='new:" + name + "'")) { assertTrue(rows.next()); assertEquals(1001, rows.getInt(1)); }
				try (var rows = statement.executeQuery("SELECT value FROM temp.trace WHERE db='" + name + "'")) { assertTrue(rows.next()); assertEquals("new:" + name, rows.getString(1)); assertFalse(rows.next()); }
				try (var rows = statement.executeQuery("SELECT count(*) FROM " + prefix + "sqlite_master WHERE name LIKE 'sqlapp_rebuild_%'")) { assertTrue(rows.next()); assertEquals(0, rows.getInt(1)); }
			}
			try (var rows = statement.executeQuery("SELECT count(*) FROM temp.combined")) { assertTrue(rows.next()); assertEquals(6, rows.getInt(1)); }
		}
	}

	@Test
	void rejectsUnjournaledTempDependenciesWithoutChangingDefinitions() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(id INT PRIMARY KEY)"); statement.execute("INSERT INTO t VALUES(1)"); statement.execute("CREATE TEMP VIEW v AS SELECT id FROM main.t");
			try (var rows = statement.executeQuery("PRAGMA temp.journal_mode=OFF")) { assertTrue(rows.next()); }
			var before = temporaryDefinitions(connection);
			assertThrows(IllegalArgumentException.class, () -> new SqliteTableRebuilder().rebuild(connection, read(connection, "main")));
			assertTrue(connection.getAutoCommit()); assertEquals(before, temporaryDefinitions(connection));
			try (var rows = statement.executeQuery("SELECT id FROM temp.v")) { assertTrue(rows.next()); assertEquals(1, rows.getInt(1)); }
		}
	}

	private List<String> temporaryDefinitions(Connection connection) throws SQLException {
		var definitions = new ArrayList<String>();
		try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT type,name,sql FROM temp.sqlite_master WHERE type IN ('view','trigger') ORDER BY type,name")) {
			while (rows.next()) definitions.add(rows.getString(1) + ":" + rows.getString(2) + ":" + rows.getString(3));
		}
		return definitions;
	}
	private Table read(Connection connection, String database) {
		var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader(); reader.setSchemaName(database); reader.setObjectName("t"); return reader.getAllFull(connection).get(0);
	}
	private static String quote(String name) { return "\"" + name.replace("\"", "\"\"") + "\""; }
}
