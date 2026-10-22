/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.data.db.datatype.DataType;

class SqliteTableRebuilderTest {
	private static final List<String> DATABASES = List.of("main", "temp", "other\" db");

	@ParameterizedTest
	@CsvSource({"main,success","temp,success","other\" db,success","main,copy","temp,copy","other\" db,copy","main,index","temp,index","other\" db,index"})
	void rebuildsRowsIndexesIdentityAndForeignKeysOrRestoresEverything(String database, String outcome) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = connection.createStatement()) {
			statement.execute("PRAGMA foreign_keys=ON");
			statement.execute("ATTACH ':memory:' AS " + quote(DATABASES.get(2)));
			for (String name : DATABASES) {
				String prefix = quote(name) + ".";
				statement.execute("CREATE TABLE " + prefix + "t(id INTEGER PRIMARY KEY AUTOINCREMENT, payload TEXT, obsolete INT, computed TEXT AS (upper(payload))) STRICT");
				statement.execute("CREATE INDEX " + prefix + "expr ON t(lower(payload)) WHERE payload<>''");
				statement.execute("CREATE TABLE " + prefix + "child(parent_id INT REFERENCES t(id) ON DELETE CASCADE)");
				statement.execute("CREATE TABLE " + prefix + "other(value TEXT)");
				statement.execute("CREATE INDEX " + prefix + "collision ON other(value)");
				statement.execute("INSERT INTO " + prefix + "t(id,payload,obsolete) VALUES(10,'alpha',1),(20,'beta',2),(1000,'deleted',3)");
				statement.execute("DELETE FROM " + prefix + "t WHERE id=1000");
				statement.execute("INSERT INTO " + prefix + "child VALUES(10),(20)");
			}
			var target = read(connection, database, "t");
			var payload = target.getColumns().get("payload"); target.getColumns().remove("payload"); target.getColumns().add(0, payload);
			target.getColumns().remove("obsolete");
			var note = new Column("note").setDataType(DataType.VARCHAR).setDataTypeName("TEXT").setDefaultValue("'new'"); target.getColumns().add(note);
			var stored = new Column("stored_length").setDataType(DataType.INT).setDataTypeName("INTEGER").setFormula("length(payload)").setFormulaPersisted(true); target.getColumns().add(stored);
			var unique = new UniqueConstraint("payload_unique"); unique.getColumns().add(payload); target.getConstraints().add(unique);
			if (outcome.equals("copy")) target.getConstraints().add(new CheckConstraint("reject_old_rows", "id<15"));
			if (outcome.equals("index")) { var index = new com.sqlapp.data.schemas.Index("collision"); index.getColumns().add(payload); target.getIndexes().add(index); }
			String xml = target.asXml();
			if (outcome.equals("success")) {
				var result = new SqliteTableRebuilder().rebuild(connection, target);
				assertEquals(2, result.rowCount()); assertFalse(result.operations().isEmpty());
				assertThrows(UnsupportedOperationException.class, () -> result.operations().clear());
			} else assertThrows(SQLException.class, () -> new SqliteTableRebuilder().rebuild(connection, target));
			assertEquals(xml, target.asXml()); assertTrue(connection.getAutoCommit());
			try (var rows = statement.executeQuery("PRAGMA foreign_keys")) { assertTrue(rows.next()); assertEquals(1, rows.getInt(1)); }
			for (String name : DATABASES) {
				boolean changed = name.equals(database) && outcome.equals("success"); String prefix = quote(name) + ".";
				try (var rows = statement.executeQuery("SELECT id,payload,computed" + (changed ? ",note,stored_length" : ",obsolete") + " FROM " + prefix + "t ORDER BY id")) {
					assertTrue(rows.next()); assertEquals(10, rows.getInt(1)); assertEquals("alpha", rows.getString(2)); assertEquals("ALPHA", rows.getString(3));
					if (changed) { assertEquals("new", rows.getString(4)); assertEquals(5, rows.getInt(5)); } else assertEquals(1, rows.getInt(4));
					assertTrue(rows.next()); assertEquals(20, rows.getInt(1)); assertFalse(rows.next());
				}
				try (var rows = statement.executeQuery("SELECT count(*) FROM " + prefix + "child")) { assertTrue(rows.next()); assertEquals(2, rows.getInt(1)); }
				statement.execute("INSERT INTO " + prefix + "t(payload) VALUES('next')");
				try (var rows = statement.executeQuery("SELECT id FROM " + prefix + "t WHERE payload='next'")) { assertTrue(rows.next()); assertEquals(1001, rows.getInt(1)); }
				try (var rows = statement.executeQuery("SELECT payload FROM " + prefix + "t INDEXED BY expr WHERE payload<>'' AND lower(payload)='alpha'")) { assertTrue(rows.next()); assertEquals("alpha", rows.getString(1)); }
				try (var rows = statement.executeQuery("SELECT count(*) FROM " + prefix + "sqlite_master WHERE name LIKE 'sqlapp_rebuild_%'")) { assertTrue(rows.next()); assertEquals(0, rows.getInt(1)); }
			}
		}
	}

	@ParameterizedTest
	@CsvSource({"true,true","true,false","false,true","false,false"})
	void validatesNewForeignKeyEvenWhenEnforcementWasInitiallyDisabled(boolean enabled, boolean valid) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = connection.createStatement()) {
			statement.execute("PRAGMA foreign_keys=" + (enabled ? 1 : 0));
			statement.execute("CREATE TABLE parent(id INT PRIMARY KEY)"); statement.execute("INSERT INTO parent VALUES(1)");
			statement.execute("CREATE TABLE t(id INT PRIMARY KEY,parent_id INT)");
			statement.execute("INSERT INTO t VALUES(10," + (valid ? 1 : 999) + ")");
			statement.execute("CREATE TABLE template(id INT PRIMARY KEY,parent_id INT REFERENCES parent(id))");
			var target = read(connection, "main", "template"); target.setName("t");
			if (valid) new SqliteTableRebuilder().rebuild(connection, target);
			else assertThrows(SQLException.class, () -> new SqliteTableRebuilder().rebuild(connection, target));
			assertTrue(connection.getAutoCommit());
			try (var rows = statement.executeQuery("PRAGMA foreign_keys")) { assertTrue(rows.next()); assertEquals(enabled ? 1 : 0, rows.getInt(1)); }
			var actual = read(connection, "main", "t");
			assertEquals(valid ? 1 : 0, actual.getConstraints().getForeignKeyConstraints().size());
			try (var rows = statement.executeQuery("SELECT parent_id FROM t")) { assertTrue(rows.next()); assertEquals(valid ? 1 : 999, rows.getInt(1)); }
		}
	}

	@Test
	void preservesHiddenRowidAndEmptyAutoincrementHighWaterMark() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(rowid TEXT,payload TEXT)"); statement.execute("INSERT INTO t(_rowid_,rowid,payload) VALUES(50,'named','value')");
			var target = read(connection, "main", "t"); target.getColumns().get("payload").setDefaultValue("'new'");
			new SqliteTableRebuilder().rebuild(connection, target);
			try (var rows = statement.executeQuery("SELECT _rowid_,rowid,payload FROM t")) { assertTrue(rows.next()); assertEquals(50, rows.getInt(1)); assertEquals("named", rows.getString(2)); assertEquals("value", rows.getString(3)); }
			statement.execute("CREATE TABLE empty(id INTEGER PRIMARY KEY AUTOINCREMENT)"); statement.execute("INSERT INTO empty VALUES(1000)"); statement.execute("DELETE FROM empty");
			assertEquals(0, new SqliteTableRebuilder().rebuild(connection, read(connection, "main", "empty")).rowCount());
			statement.execute("INSERT INTO empty DEFAULT VALUES");
			try (var rows = statement.executeQuery("SELECT id FROM empty")) { assertTrue(rows.next()); assertEquals(1001, rows.getInt(1)); }
		}
	}

	@Test
	void rejectsUnsupportedDependenciesAndCallerTransactions() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = connection.createStatement()) {
			statement.execute("PRAGMA foreign_keys=ON"); statement.execute("CREATE TABLE t(id INTEGER PRIMARY KEY,value TEXT)");
			var target = read(connection, "main", "t");
			connection.setAutoCommit(false);
			assertThrows(IllegalArgumentException.class, () -> new SqliteTableRebuilder().rebuild(connection, target));
			assertFalse(connection.getAutoCommit()); connection.rollback(); connection.setAutoCommit(true);
			try (var rows = statement.executeQuery("PRAGMA journal_mode=OFF")) { assertTrue(rows.next()); assertEquals("off", rows.getString(1)); }
			assertThrows(IllegalArgumentException.class, () -> new SqliteTableRebuilder().rebuild(connection, target)); try (var rows = statement.executeQuery("PRAGMA journal_mode=MEMORY")) { assertTrue(rows.next()); }
			statement.execute("CREATE VIEW v AS SELECT * FROM t");
			new SqliteTableRebuilder().rebuild(connection, target); statement.execute("DROP VIEW v");
			statement.execute("CREATE TEMP TRIGGER tr AFTER INSERT ON main.t BEGIN SELECT 1; END");
			new SqliteTableRebuilder().rebuild(connection, target); statement.execute("DROP TRIGGER temp.tr");
			var mode = target.clone(); mode.getSpecifics().put("without_rowid", "true");
			assertThrows(IllegalArgumentException.class, () -> new SqliteTableRebuilder().rebuild(connection, mode));
			statement.execute("PRAGMA ignore_check_constraints=ON");
			assertThrows(IllegalArgumentException.class, () -> new SqliteTableRebuilder().rebuild(connection, target)); statement.execute("PRAGMA ignore_check_constraints=OFF");
			statement.execute("CREATE TABLE inaccessible(rowid TEXT,_rowid_ TEXT,oid TEXT)");
			assertThrows(IllegalArgumentException.class, () -> new SqliteTableRebuilder().rebuild(connection, read(connection, "main", "inaccessible")));
			statement.execute("CREATE TABLE self(id INT PRIMARY KEY,parent_id INT REFERENCES self(id))");
			assertEquals(0, new SqliteTableRebuilder().rebuild(connection, read(connection, "main", "self")).rowCount());
			statement.execute("CREATE VIRTUAL TABLE virtual USING fts5(value)");
			assertEquals(0, new SqliteTableRebuilder().rebuild(connection, read(connection, "main", "virtual")).rowCount());
			assertTrue(connection.getAutoCommit());
			try (var rows = statement.executeQuery("PRAGMA foreign_keys")) { assertTrue(rows.next()); assertEquals(1, rows.getInt(1)); }
		}
	}

	@ParameterizedTest
	@CsvSource({"true,true","true,false","false,true","false,false"})
	void changesTypesAndCompositePrimaryKeyWithNativeStrictValidation(boolean withoutRowid, boolean valid) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = connection.createStatement()) {
			String options = withoutRowid ? " WITHOUT ROWID, STRICT" : " STRICT";
			statement.execute("CREATE TABLE t(key TEXT PRIMARY KEY,value TEXT)" + options);
			statement.execute("INSERT INTO t(" + (withoutRowid ? "" : "rowid,") + "key,value) VALUES(" + (withoutRowid ? "" : "50,") + "'k','" + (valid ? "0012" : "invalid") + "')");
			statement.execute("CREATE TABLE template(value INTEGER,key TEXT,extra TEXT DEFAULT 'added',PRIMARY KEY(key,extra))" + options);
			var target = read(connection, "main", "template"); target.setName("t");
			if (valid) assertEquals(1, new SqliteTableRebuilder().rebuild(connection, target).rowCount());
			else assertThrows(SQLException.class, () -> new SqliteTableRebuilder().rebuild(connection, target));
			try (var rows = statement.executeQuery("SELECT key,value,typeof(value)" + (valid ? ",extra" : "") + (withoutRowid ? "" : ",rowid") + " FROM t")) {
				assertTrue(rows.next()); assertEquals("k", rows.getString(1)); assertEquals(valid ? "12" : "invalid", rows.getString(2));
				assertEquals(valid ? "integer" : "text", rows.getString(3));
				if (valid) assertEquals("added", rows.getString(4));
				if (!withoutRowid) assertEquals(50, rows.getInt(valid ? 5 : 4));
				assertFalse(rows.next());
			}
		}
	}

	@ParameterizedTest
	@CsvSource({"main,success","temp,success","other\" db,success","main,view","temp,view","other\" db,view",
		"main,trigger","temp,trigger","other\" db,trigger","main,updateof","temp,updateof","other\" db,updateof",
		"main,index","temp,index","other\" db,index","main,copy","temp,copy","other\" db,copy"})
	void restoresViewsAndTriggersWithoutCopySideEffectsAndRollsBackBrokenDependencies(String database, String outcome) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = connection.createStatement()) {
			statement.execute("PRAGMA foreign_keys=ON"); statement.execute("ATTACH ':memory:' AS " + quote(DATABASES.get(2)));
			for (String name : DATABASES) {
				String prefix = quote(name) + ".";
				statement.execute("CREATE TABLE " + prefix + "t(id INTEGER PRIMARY KEY AUTOINCREMENT,payload TEXT,obsolete INT)");
				statement.execute("CREATE TABLE " + prefix + "audit(message TEXT)");
				statement.execute("CREATE TABLE " + prefix + "other(value INT)");
				statement.execute("CREATE TABLE " + prefix + "child(id INT REFERENCES t(id) ON DELETE CASCADE)");
				statement.execute("CREATE INDEX " + prefix + "collision ON other(value)");
				statement.execute("CREATE INDEX " + prefix + "expression_idx ON t(lower(payload))");
				statement.execute("INSERT INTO " + prefix + "t VALUES(10,'initial',1),(1000,'deleted',2)");
				statement.execute("DELETE FROM " + prefix + "t WHERE id=1000");
				statement.execute("INSERT INTO " + prefix + "child VALUES(10)");
			}
			String prefix = quote(database) + ".";
			statement.execute("CREATE VIEW " + prefix + "base_view AS SELECT id,payload FROM t");
			statement.execute("CREATE VIEW " + prefix + "dependent AS SELECT payload FROM base_view");
			statement.execute("CREATE VIEW " + prefix + "insert_view AS SELECT id,payload FROM t");
			statement.execute("CREATE VIEW " + prefix + "delete_view AS SELECT id,payload FROM t");
			statement.execute("CREATE VIEW " + prefix + "unrelated AS SELECT value FROM other");
			for (String event : List.of("INSERT", "UPDATE", "DELETE")) {
				statement.execute("CREATE TRIGGER " + prefix + quote("audit_" + event) + " AFTER " + event + " ON " + prefix + "t BEGIN INSERT INTO audit VALUES('" + event + "'); END");
			}
			statement.execute("CREATE TRIGGER " + prefix + "view_update INSTEAD OF UPDATE OF payload ON " + prefix + "base_view BEGIN UPDATE t SET payload=NEW.payload WHERE id=OLD.id; END");
			statement.execute("CREATE TRIGGER " + prefix + "view_insert INSTEAD OF INSERT ON " + prefix + "insert_view BEGIN INSERT INTO t(id,payload) VALUES(NEW.id,NEW.payload); END");
			statement.execute("CREATE TRIGGER " + prefix + "view_delete INSTEAD OF DELETE ON " + prefix + "delete_view BEGIN DELETE FROM t WHERE id=OLD.id; END");
			statement.execute("CREATE TRIGGER " + prefix + "other_insert AFTER INSERT ON " + prefix + "other BEGIN INSERT INTO audit VALUES('other'); END");
			if (outcome.equals("view")) statement.execute("CREATE VIEW " + prefix + "invalid_after_rebuild AS SELECT obsolete FROM t");
			if (outcome.equals("trigger")) statement.execute("CREATE TRIGGER " + prefix + "invalid_after_rebuild AFTER INSERT ON " + prefix + "t BEGIN INSERT INTO audit VALUES(NEW.obsolete); END");
			if (outcome.equals("updateof")) statement.execute("CREATE TRIGGER " + prefix + "invalid_after_rebuild AFTER UPDATE OF obsolete ON " + prefix + "t BEGIN SELECT 1; END");
			var target = read(connection, database, "t"); target.getColumns().remove("obsolete");
			target.getColumns().add(new Column("note").setDataType(DataType.VARCHAR).setDataTypeName("TEXT").setDefaultValue("'new'"));
			if (outcome.equals("copy")) target.getConstraints().add(new CheckConstraint("reject_copy", "id<0"));
			if (outcome.equals("index")) {
				var index = new com.sqlapp.data.schemas.Index("collision"); index.getColumns().add(target.getColumns().get("payload")); target.getIndexes().add(index);
			}
			String xml = target.asXml();
			if (outcome.equals("success")) {
				var result = new SqliteTableRebuilder().rebuild(connection, target);
				assertEquals(1, result.rowCount());
				assertTrue(result.operations().stream().anyMatch(operation -> operation.getSqlText().contains("DROP VIEW")));
				assertTrue(result.operations().stream().anyMatch(operation -> operation.getSqlText().contains("CREATE TRIGGER")));
			} else assertThrows(SQLException.class, () -> new SqliteTableRebuilder().rebuild(connection, target));
			assertEquals(xml, target.asXml()); assertTrue(connection.getAutoCommit());
			try (var rows = statement.executeQuery("PRAGMA foreign_keys")) { assertTrue(rows.next()); assertEquals(1, rows.getInt(1)); }
			for (String name : DATABASES) {
				try (var rows = statement.executeQuery("SELECT count(*) FROM " + quote(name) + ".audit")) { assertTrue(rows.next()); assertEquals(0, rows.getInt(1)); }
				try (var rows = statement.executeQuery("SELECT payload FROM " + quote(name) + ".t WHERE id=10")) { assertTrue(rows.next()); assertEquals("initial", rows.getString(1)); }
			}
			try (var rows = statement.executeQuery("SELECT payload FROM " + prefix + "dependent")) { assertTrue(rows.next()); assertEquals("initial", rows.getString(1)); }
			statement.execute("UPDATE " + prefix + "base_view SET payload='changed' WHERE id=10");
			statement.execute("INSERT INTO " + prefix + "insert_view VALUES(11,'added')");
			statement.execute("DELETE FROM " + prefix + "delete_view WHERE id=11");
			statement.execute("INSERT INTO " + prefix + "other VALUES(5)");
			for (String message : List.of("INSERT", "UPDATE", "DELETE", "other")) {
				try (var rows = statement.executeQuery("SELECT count(*) FROM " + prefix + "audit WHERE message='" + message + "'")) { assertTrue(rows.next()); assertEquals(1, rows.getInt(1)); }
			}
			try (var rows = statement.executeQuery("SELECT value FROM " + prefix + "unrelated")) { assertTrue(rows.next()); assertEquals(5, rows.getInt(1)); }
			try (var rows = statement.executeQuery("SELECT count(*) FROM " + prefix + "child")) { assertTrue(rows.next()); assertEquals(1, rows.getInt(1)); }
			statement.execute("INSERT INTO " + prefix + "t(payload) VALUES('next')");
			try (var rows = statement.executeQuery("SELECT id FROM " + prefix + "t WHERE payload='next'")) { assertTrue(rows.next()); assertEquals(1001, rows.getInt(1)); }
			try (var rows = statement.executeQuery("SELECT count(*) FROM " + prefix + "sqlite_master WHERE name LIKE 'sqlapp_rebuild_%'")) { assertTrue(rows.next()); assertEquals(0, rows.getInt(1)); }
		}
	}
	private Table read(Connection connection, String database, String name) {
		var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader(); reader.setSchemaName(database); reader.setObjectName(name); return reader.getAllFull(connection).get(0);
	}

	@Test
	void updateOfValidationDistinguishesQuotedNamesCommentsAndBodyText() throws Exception {
		String definition = "CREATE TRIGGER \"UPDATE OF fake\" AFTER UPDATE OF \"ON\", /* ON ignored */ \"a,b\" ON t BEGIN SELECT 'UPDATE OF false ON body'; END";
		assertEquals(List.of("ON", "a,b"), com.sqlapp.data.db.dialect.sqlite.util.SqliteDdl.triggerUpdateColumns(definition));
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(id INT PRIMARY KEY,\"ON\" INT,\"a,b\" INT)");
			statement.execute("INSERT INTO t VALUES(1,2,3)"); statement.execute(definition);
			var target = read(connection, "main", "t"); target.getColumns().remove("a,b");
			assertThrows(SQLException.class, () -> new SqliteTableRebuilder().rebuild(connection, target));
			statement.execute("UPDATE t SET \"a,b\"=4");
			try (var rows = statement.executeQuery("SELECT \"ON\",\"a,b\" FROM t")) { assertTrue(rows.next()); assertEquals(2, rows.getInt(1)); assertEquals(4, rows.getInt(2)); }
		}
	}
	private static String quote(String name) { return "\"" + name.replace("\"", "\"\"") + "\""; }
}
