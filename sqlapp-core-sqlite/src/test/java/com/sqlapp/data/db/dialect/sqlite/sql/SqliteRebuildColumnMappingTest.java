/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class SqliteRebuildColumnMappingTest {
	private static final List<String> DATABASES = List.of("main", "temp", "other\" db");
	private static final String NEW_ID = "new\" id", NEW_VALUE = "new value";

	@ParameterizedTest
	@CsvSource({ "main,false", "temp,false", "other\" db,false", "main,true", "temp,true", "other\" db,true" })
	void explicitlyRenamesIdentityAndMixedStorageColumnsOrRollsBack(String database, boolean fail) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("PRAGMA foreign_keys=ON");
			statement.execute("ATTACH ':memory:' AS " + quote(DATABASES.get(2)));
			for (String name : DATABASES) {
				String prefix = quote(name) + ".";
				statement.execute("CREATE TABLE " + prefix
						+ "t(id INTEGER PRIMARY KEY AUTOINCREMENT,\"old value\" ANY,qty INT) STRICT");
				statement.execute("INSERT INTO " + prefix
						+ "t VALUES(10,'000123',1),(20,X'0102',2),(30,9007199254740993,3),(1000,'deleted',4)");
				statement.execute("DELETE FROM " + prefix + "t WHERE id=1000");
			}
			var target = read(connection, database, "t");
			var id = target.getColumns().get("id");
			id.setName(NEW_ID);
			target.getPrimaryKeyConstraint().getColumns().clear();
			target.getPrimaryKeyConstraint().getColumns().add(id);
			target.getColumns().get("old value").setName(NEW_VALUE);
			target.getColumns().get("qty").setName("count");
			target.getColumns().add(new Column("note").setDataType(DataType.VARCHAR).setDataTypeName("TEXT")
					.setDefaultValue("'added'"));
			var mappings = Map.of(NEW_ID, "id", NEW_VALUE, "old value", "count", fail ? "old value" : "qty");
			String xml = target.asXml();
			if (fail)
				assertThrows(SQLException.class,
						() -> new SqliteTableRebuilder().rebuild(connection, target, mappings));
			else
				assertEquals(3, new SqliteTableRebuilder().rebuild(connection, target, mappings).rowCount());
			assertEquals(xml, target.asXml());
			assertTrue(connection.getAutoCommit());
			for (String name : DATABASES) {
				boolean changed = name.equals(database) && !fail;
				String prefix = quote(name) + ".";
				String idName = changed ? NEW_ID : "id", valueName = changed ? NEW_VALUE : "old value",
						qtyName = changed ? "count" : "qty";
				try (var rows = statement.executeQuery("SELECT " + quote(idName) + "," + quote(valueName) + ",typeof("
						+ quote(valueName) + ")," + quote(qtyName) + (changed ? ",note" : "") + " FROM " + prefix
						+ "t ORDER BY " + quote(idName))) {
					assertTrue(rows.next());
					assertEquals(10, rows.getInt(1));
					assertEquals("000123", rows.getString(2));
					assertEquals("text", rows.getString(3));
					assertEquals(1, rows.getInt(4));
					if (changed)
						assertEquals("added", rows.getString(5));
					assertTrue(rows.next());
					assertEquals(20, rows.getInt(1));
					assertArrayEquals(new byte[] { 1, 2 }, rows.getBytes(2));
					assertEquals("blob", rows.getString(3));
					assertEquals(2, rows.getInt(4));
					assertTrue(rows.next());
					assertEquals(30, rows.getInt(1));
					assertEquals(9007199254740993L, rows.getLong(2));
					assertEquals("integer", rows.getString(3));
					assertEquals(3, rows.getInt(4));
					assertFalse(rows.next());
				}
				statement.execute("INSERT INTO " + prefix + "t(" + quote(valueName) + "," + quote(qtyName)
						+ ") VALUES('next',4)");
				try (var rows = statement.executeQuery(
						"SELECT " + quote(idName) + " FROM " + prefix + "t WHERE " + quote(qtyName) + "=4")) {
					assertTrue(rows.next());
					assertEquals(1001, rows.getInt(1));
				}
				try (var rows = statement.executeQuery(
						"SELECT count(*) FROM " + prefix + "sqlite_master WHERE name LIKE 'sqlapp_rebuild_%'")) {
					assertTrue(rows.next());
					assertEquals(0, rows.getInt(1));
				}
			}
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "main", "temp", "other\" db" })
	void mapsCompositeSelfKeysOnWithoutRowidTables(String database) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("PRAGMA foreign_keys=ON");
			statement.execute("ATTACH ':memory:' AS " + quote(DATABASES.get(2)));
			String prefix = quote(database) + ".";
			statement.execute("CREATE TABLE " + prefix
					+ "t(tenant TEXT,id INT,parent_tenant TEXT,parent_id INT,value TEXT,PRIMARY KEY(tenant,id),FOREIGN KEY(parent_tenant,parent_id) REFERENCES t(tenant,id)) WITHOUT ROWID");
			statement.execute("INSERT INTO " + prefix + "t VALUES('x',1,NULL,NULL,'root'),('x',2,'x',1,'child')");
			statement.execute("CREATE TABLE " + prefix
					+ "model(new_tenant TEXT,new_id INT,new_parent_tenant TEXT,new_parent_id INT,new_value TEXT,PRIMARY KEY(new_tenant,new_id),FOREIGN KEY(new_parent_tenant,new_parent_id) REFERENCES model(new_tenant,new_id) ON UPDATE CASCADE ON DELETE CASCADE) WITHOUT ROWID");
			var target = read(connection, database, "model");
			target.setName("t");
			target.getConstraints().getForeignKeyConstraints().get(0).setRelatedTableName("t");
			var mappings = Map.of("new_tenant", "tenant", "new_id", "id", "new_parent_tenant", "parent_tenant",
					"new_parent_id", "parent_id", "new_value", "value");
			assertEquals(2, new SqliteTableRebuilder().rebuild(connection, target, mappings).rowCount());
			statement.execute("UPDATE " + prefix + "t SET new_id=10 WHERE new_id=1");
			try (var rows = statement
					.executeQuery("SELECT new_parent_id,new_value FROM " + prefix + "t WHERE new_id=2")) {
				assertTrue(rows.next());
				assertEquals(10, rows.getInt(1));
				assertEquals("child", rows.getString(2));
			}
			statement.execute("DELETE FROM " + prefix + "t WHERE new_id=10");
			try (var rows = statement.executeQuery("SELECT count(*) FROM " + prefix + "t")) {
				assertTrue(rows.next());
				assertEquals(0, rows.getInt(1));
			}
		}
	}

	@Test
	void preservesImplicitRowidWhenRenamingARealRowidColumn() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(rowid TEXT,value TEXT)");
			statement.execute("INSERT INTO t(_rowid_,rowid,value) VALUES(50,'named','kept')");
			var target = read(connection, "main", "t");
			target.getColumns().get("rowid").setName("named_rowid");
			target.getColumns().get("value").setName("new_value");
			new SqliteTableRebuilder().rebuild(connection, target,
					Map.of("named_rowid", "rowid", "new_value", "value"));
			try (var rows = statement.executeQuery("SELECT _rowid_,named_rowid,new_value FROM t")) {
				assertTrue(rows.next());
				assertEquals(50, rows.getInt(1));
				assertEquals("named", rows.getString(2));
				assertEquals("kept", rows.getString(3));
			}
		}
	}

	@Test
	void invalidMappingsAndUnmappedIdentityChangesFailBeforeReplacement() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("PRAGMA foreign_keys=ON");
			statement.execute(
					"CREATE TABLE t(id INTEGER PRIMARY KEY,value TEXT,qty INT,calculated TEXT AS (value||'!'))");
			statement.execute("INSERT INTO t(id,value,qty) VALUES(40,'old',3)");
			var target = read(connection, "main", "t");
			var id = target.getColumns().get("id");
			id.setName(NEW_ID);
			target.getPrimaryKeyConstraint().getColumns().clear();
			target.getPrimaryKeyConstraint().getColumns().add(id);
			assertThrows(IllegalArgumentException.class, () -> new SqliteTableRebuilder().rebuild(connection, target));
			for (var invalid : List.of(Map.of("missing", "value"), Map.of("value", "missing"),
					Map.of("calculated", "value"), Map.of(NEW_ID, "qty"), Map.of("value", "upper(value)"),
					Map.of(NEW_ID, "id", "NEW\" ID", "id"))) {
				var mappings = new LinkedHashMap<String, String>();
				mappings.put(NEW_ID, "id");
				mappings.putAll(invalid);
				assertThrows(IllegalArgumentException.class,
						() -> new SqliteTableRebuilder().rebuild(connection, target, mappings));
			}
			var nullValue = new LinkedHashMap<String, String>();
			nullValue.put(NEW_ID, null);
			assertThrows(IllegalArgumentException.class,
					() -> new SqliteTableRebuilder().rebuild(connection, target, nullValue));
			assertTrue(connection.getAutoCommit());
			try (var rows = statement.executeQuery("PRAGMA foreign_keys")) {
				assertTrue(rows.next());
				assertEquals(1, rows.getInt(1));
			}
			try (var rows = statement.executeQuery("SELECT id,value,calculated FROM t")) {
				assertTrue(rows.next());
				assertEquals(40, rows.getInt(1));
				assertEquals("old", rows.getString(2));
				assertEquals("old!", rows.getString(3));
			}
		}
	}

	@Test
	void rollsBackMappedRenameWhenATempDependencyStillUsesTheOldName() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(id INTEGER PRIMARY KEY,value TEXT)");
			statement.execute("INSERT INTO t VALUES(40,'old')");
			statement.execute("CREATE TEMP VIEW v AS SELECT id,value FROM main.t");
			var target = read(connection, "main", "t");
			var id = target.getColumns().get("id");
			id.setName(NEW_ID);
			target.getPrimaryKeyConstraint().getColumns().clear();
			target.getPrimaryKeyConstraint().getColumns().add(id);
			assertThrows(SQLException.class,
					() -> new SqliteTableRebuilder().rebuild(connection, target, Map.of(NEW_ID, "id")));
			try (var rows = statement.executeQuery("SELECT id,value FROM temp.v")) {
				assertTrue(rows.next());
				assertEquals(40, rows.getInt(1));
				assertEquals("old", rows.getString(2));
			}
			assertTrue(connection.getAutoCommit());
		}
	}

	private Table read(Connection connection, String database, String name) {
		var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setSchemaName(database);
		reader.setObjectName(name);
		return reader.getAllFull(connection).get(0);
	}

	private static String quote(String name) {
		return "\"" + name.replace("\"", "\"\"") + "\"";
	}
}
