/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;

class SqliteSelfReferencingRebuildTest {
	private static final List<String> DATABASES = List.of("main", "temp", "other\" db");
	private static final String TABLE = "node\" table";

	static Stream<Arguments> variants() {
		return DATABASES.stream().flatMap(database -> Stream.of("tree", "cycle", "composite")
				.flatMap(kind -> Stream.of(false, true).flatMap(legacy -> Stream.of(false, true)
						.map(fail -> Arguments.of(database, kind, legacy, fail)))));
	}

	@ParameterizedTest
	@MethodSource("variants")
	void preservesSelfReferencesAndCascadeBehaviorOrRollsBack(String database, String kind, boolean legacy, boolean fail) throws Exception {
		boolean composite = kind.equals("composite");
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = connection.createStatement()) {
			statement.execute("PRAGMA foreign_keys=ON"); statement.execute("PRAGMA legacy_alter_table=" + (legacy ? 1 : 0));
			statement.execute("ATTACH ':memory:' AS " + quote(DATABASES.get(2)));
			for (String name : DATABASES) {
				String prefix = quote(name) + ".";
				if (composite) {
					statement.execute("CREATE TABLE " + prefix + quote(TABLE) + "(tenant TEXT,id INT,parent_tenant TEXT,parent_id INT,payload TEXT,PRIMARY KEY(tenant,id),CONSTRAINT self_fk FOREIGN KEY(parent_tenant,parent_id) REFERENCES " + quote(TABLE) + "(tenant,id) ON UPDATE CASCADE ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED) WITHOUT ROWID");
					statement.execute("CREATE TABLE " + prefix + "child(tenant TEXT,id INT,FOREIGN KEY(tenant,id) REFERENCES " + quote(TABLE) + "(tenant,id) ON UPDATE CASCADE ON DELETE SET NULL)");
				} else {
					statement.execute("CREATE TABLE " + prefix + quote(TABLE) + "(id INTEGER PRIMARY KEY AUTOINCREMENT,parent_id INT,payload TEXT,CONSTRAINT self_fk FOREIGN KEY(parent_id) REFERENCES " + quote(TABLE) + "(id) ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)");
					statement.execute("CREATE TABLE " + prefix + "child(id INT REFERENCES " + quote(TABLE) + "(id) ON UPDATE CASCADE ON DELETE SET NULL)");
				}
				statement.execute("CREATE TABLE " + prefix + "audit(message TEXT)");
				statement.execute("CREATE INDEX " + prefix + "parent_idx ON " + quote(TABLE) + "(parent_id)");
				connection.setAutoCommit(false);
				if (composite) statement.execute("INSERT INTO " + prefix + quote(TABLE) + " VALUES('x',1,NULL,NULL,'a'),('x',2,'x',1,'b'),('y',1,'x',2,'c')");
				else statement.execute("INSERT INTO " + prefix + quote(TABLE) + (kind.equals("cycle") ? " VALUES(1,2,'a'),(2,1,'b'),(3,3,'c')" : " VALUES(1,NULL,'a'),(2,1,'b'),(3,2,'c')"));
				connection.commit(); connection.setAutoCommit(true);
				statement.execute("INSERT INTO " + prefix + "child VALUES(" + (composite ? "'x'," : "") + "2)");
			}
			String prefix = quote(database) + ".", identity = prefix + quote(TABLE);
			statement.execute("CREATE VIEW " + prefix + "node_view AS SELECT payload FROM " + quote(TABLE));
			statement.execute("CREATE TRIGGER " + prefix + "node_update AFTER UPDATE ON " + identity + " BEGIN INSERT INTO audit VALUES('updated'); END");
			var target = read(connection, database);
			target.getColumns().add(new Column("note").setDataType(DataType.VARCHAR).setDataTypeName("TEXT").setDefaultValue("'added'"));
			if (fail) {
				var parentKey = new UniqueConstraint("wrong_parent_key");
				if (composite) parentKey.getColumns().add(target.getColumns().get("tenant"));
				parentKey.getColumns().add(target.getColumns().get("payload")); target.getConstraints().add(parentKey);
				var foreignKey = target.getConstraints().getForeignKeyConstraints().get(0);
				foreignKey.getRelatedColumns().clear();
				if (composite) foreignKey.getRelatedColumns().add(target.getColumns().get("tenant"));
				foreignKey.getRelatedColumns().add(target.getColumns().get("payload"));
			}
			String xml = target.asXml();
			if (fail) assertThrows(SQLException.class, () -> new SqliteTableRebuilder().rebuild(connection, target));
			else {
				var result = new SqliteTableRebuilder().rebuild(connection, target); assertEquals(3, result.rowCount());
				String create = result.operations().stream().map(operation -> operation.getSqlText()).filter(sql -> sql.startsWith("CREATE TABLE")).findFirst().orElseThrow();
				assertFalse(create.substring(create.indexOf("REFERENCES")).contains("sqlapp_rebuild_"));
			}
			assertEquals(xml, target.asXml()); assertTrue(connection.getAutoCommit());
			try (var rows = statement.executeQuery("PRAGMA foreign_keys")) { assertTrue(rows.next()); assertEquals(1, rows.getInt(1)); }
			try (var rows = statement.executeQuery("PRAGMA legacy_alter_table")) { assertTrue(rows.next()); assertEquals(legacy ? 1 : 0, rows.getInt(1)); }
			assertEquals(!fail, read(connection, database).getColumns().contains("note"));
			for (String name : DATABASES) {
				String selected = quote(name) + ".";
				try (var rows = statement.executeQuery("SELECT count(*) FROM " + selected + quote(TABLE))) { assertTrue(rows.next()); assertEquals(3, rows.getInt(1)); }
				try (var rows = statement.executeQuery("SELECT count(*) FROM " + selected + "audit")) { assertTrue(rows.next()); assertEquals(0, rows.getInt(1)); }
				try (var rows = statement.executeQuery("PRAGMA " + quote(name) + ".foreign_key_list(" + quote(TABLE) + ")")) {
					int count = 0; while (rows.next()) { assertEquals(TABLE, rows.getString("table")); assertFalse(rows.getString("to").equals("payload")); count++; }
					assertEquals(composite ? 2 : 1, count);
				}
				try (var rows = statement.executeQuery("PRAGMA " + quote(name) + ".foreign_key_check")) { assertFalse(rows.next()); }
			}
			try (var rows = statement.executeQuery("SELECT count(*) FROM " + prefix + "node_view")) { assertTrue(rows.next()); assertEquals(3, rows.getInt(1)); }
			statement.execute("UPDATE " + identity + " SET id=10 WHERE id=1" + (composite ? " AND tenant='x'" : ""));
			try (var rows = statement.executeQuery("SELECT parent_id FROM " + identity + " WHERE id=2" + (composite ? " AND tenant='x'" : ""))) { assertTrue(rows.next()); assertEquals(10, rows.getInt(1)); }
			statement.execute("DELETE FROM " + identity + " WHERE id=10" + (composite ? " AND tenant='x'" : ""));
			try (var rows = statement.executeQuery("SELECT count(*) FROM " + identity)) { assertTrue(rows.next()); assertEquals(composite ? 2 : kind.equals("cycle") ? 1 : 0, rows.getInt(1)); }
			if (composite) {
				try (var rows = statement.executeQuery("SELECT parent_tenant,parent_id FROM " + identity + " WHERE tenant='x' AND id=2")) { assertTrue(rows.next()); assertNull(rows.getObject(1)); assertNull(rows.getObject(2)); }
			} else {
				try (var rows = statement.executeQuery("SELECT id FROM " + prefix + "child")) { assertTrue(rows.next()); assertNull(rows.getObject(1)); }
			}
			try (var rows = statement.executeQuery("SELECT count(*) FROM " + prefix + "sqlite_master WHERE name LIKE 'sqlapp_rebuild_%'")) { assertTrue(rows.next()); assertEquals(0, rows.getInt(1)); }
		}
	}

	private Table read(Connection connection, String database) {
		var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader(); reader.setSchemaName(database); reader.setObjectName(TABLE); return reader.getAllFull(connection).get(0);
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void retainsMultipleSelfKeysImplicitParentColumnsAndExternalParents(boolean enabled) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = connection.createStatement()) {
			statement.execute("PRAGMA foreign_keys=" + (enabled ? 1 : 0));
			statement.execute("CREATE TABLE parent(id INT PRIMARY KEY)"); statement.execute("INSERT INTO parent VALUES(1)");
			statement.execute("CREATE TABLE " + quote(TABLE) + "(id INTEGER PRIMARY KEY,parent_id INT REFERENCES " + quote(TABLE)
					+ " ON DELETE SET NULL,alternate_id INT REFERENCES " + quote(TABLE) + "(id) ON DELETE SET NULL,external_id INT REFERENCES parent(id))");
			statement.execute("INSERT INTO " + quote(TABLE) + " VALUES(1,NULL,NULL,1),(2,1,1,1)");
			var target = read(connection, "main"); target.getColumns().add(new Column("note").setDataType(DataType.VARCHAR).setDataTypeName("TEXT").setDefaultValue("'added'"));
			String xml = target.asXml(); assertEquals(2, new SqliteTableRebuilder().rebuild(connection, target).rowCount()); assertEquals(xml, target.asXml());
			try (var rows = statement.executeQuery("PRAGMA foreign_keys")) { assertTrue(rows.next()); assertEquals(enabled ? 1 : 0, rows.getInt(1)); }
			try (var rows = statement.executeQuery("PRAGMA foreign_key_list(" + quote(TABLE) + ")")) {
				int self = 0, external = 0;
				while (rows.next()) { if (rows.getString("table").equals(TABLE)) self++; else { assertEquals("parent", rows.getString("table")); external++; } }
				assertEquals(2, self); assertEquals(1, external);
			}
			statement.execute("PRAGMA foreign_keys=ON"); statement.execute("DELETE FROM " + quote(TABLE) + " WHERE id=1");
			try (var rows = statement.executeQuery("SELECT parent_id,alternate_id,external_id,note FROM " + quote(TABLE) + " WHERE id=2")) {
				assertTrue(rows.next()); assertNull(rows.getObject(1)); assertNull(rows.getObject(2)); assertEquals(1, rows.getInt(3)); assertEquals("added", rows.getString(4));
			}
			assertThrows(SQLException.class, () -> statement.execute("DELETE FROM parent WHERE id=1"));
			try (var rows = statement.executeQuery("PRAGMA foreign_key_check")) { assertFalse(rows.next()); }
		}
	}
	private static String quote(String name) { return "\"" + name.replace("\"", "\"\"") + "\""; }
}
