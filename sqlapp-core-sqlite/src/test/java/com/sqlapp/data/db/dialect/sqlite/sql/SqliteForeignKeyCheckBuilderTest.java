/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

class SqliteForeignKeyCheckBuilderTest {
	@Test
	void detectsViolationsWithoutChangingDataOrEnforcement() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("PRAGMA foreign_keys=OFF");
			statement.execute("CREATE TABLE parent(id INTEGER PRIMARY KEY)");
			statement.execute("CREATE TABLE child(id INTEGER PRIMARY KEY,pid INT REFERENCES parent(id))");
			statement.execute("INSERT INTO child VALUES(7,99)");
			var builder = new SqliteForeignKeyCheckBuilder(DialectHolder.defaultDialect);
			for (String sql : new String[] {builder.build(), builder.table("child").build()}) {
				try (var result = statement.executeQuery(sql)) {
					assertTrue(result.next()); assertEquals("child", result.getString("table"));
					assertEquals(7, result.getLong("rowid")); assertEquals("parent", result.getString("parent"));
					assertEquals(0, result.getInt("fkid")); assertFalse(result.next());
				}
			}
			try (var result = statement.executeQuery("PRAGMA foreign_keys")) {
				assertTrue(result.next()); assertEquals(0, result.getInt(1));
			}
			try (var result = statement.executeQuery("SELECT pid FROM child WHERE id=7")) {
				assertTrue(result.next()); assertEquals(99, result.getInt(1));
			}
			statement.execute("INSERT INTO parent VALUES(99)");
			try (var result = statement.executeQuery(builder.allTables().build())) {
				assertFalse(result.next());
			}
		}
	}

	@Test
	void attachedCompositeWithoutRowidChecksRetainIdentityAndNullRowid() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("PRAGMA foreign_keys=OFF");
			statement.execute("ATTACH ':memory:' AS \"Other]Db\"");
			statement.execute("CREATE TABLE \"Other]Db\".parent(a INT,b INT,PRIMARY KEY(a,b)) WITHOUT ROWID");
			statement.execute("CREATE TABLE \"Other]Db\".\"child'odd\"(id INT PRIMARY KEY,a INT,b INT,FOREIGN KEY(a,b) REFERENCES parent(a,b)) WITHOUT ROWID");
			statement.execute("INSERT INTO \"Other]Db\".\"child'odd\" VALUES(1,10,20),(2,NULL,20)");
			var builder = new SqliteForeignKeyCheckBuilder(DialectHolder.defaultDialect);
			try (var result = statement.executeQuery(builder.build())) { assertFalse(result.next()); }
			for (String sql : new String[] {
					builder.schema(new Schema("Other]Db")).build(),
					builder.table(new Table("child'odd").setSchemaName("Other]Db")).build()}) {
				try (var result = statement.executeQuery(sql)) {
					assertTrue(result.next()); assertEquals("child'odd", result.getString("table"));
					assertNull(result.getObject("rowid")); assertEquals("parent", result.getString("parent"));
					assertEquals(0, result.getInt("fkid")); assertFalse(result.next());
				}
			}
			try (var result = statement.executeQuery(builder.table("parent").build())) { assertFalse(result.next()); }
			try (var result = statement.executeQuery(builder.allTables().build())) { assertTrue(result.next()); }
		}
	}

	@Test
	void invalidObjectsAndInjectionShapedNamesFailWithoutExecutingCommands() throws Exception {
		var builder = new SqliteForeignKeyCheckBuilder(DialectHolder.defaultDialect);
		assertThrows(IllegalArgumentException.class, () -> builder.schema(" "));
		assertThrows(IllegalArgumentException.class, () -> builder.table(""));
		assertThrows(IllegalArgumentException.class, () -> builder.table(new Table("child")));
		assertThrows(NullPointerException.class, () -> new SqliteForeignKeyCheckBuilder(null));
		assertThrows(IllegalArgumentException.class, () -> new SqliteForeignKeyCheckBuilder(
				DialectResolver.getInstance().getDialect("PostgreSQL",15,0,0)));
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE kept(id INT)");
			assertThrows(SQLException.class, () -> statement.executeQuery(builder.table("missing); DROP TABLE kept; --").build()));
			assertThrows(SQLException.class, () -> statement.executeQuery(builder.allTables().schema("missing; DROP TABLE kept; --").build()));
			try (var result = statement.executeQuery("SELECT count(*) FROM kept")) { assertTrue(result.next()); }
		}
	}

	@Test
	void detectsDeferredViolationsInsideATransactionAndLeavesRollbackToTheCaller() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("PRAGMA foreign_keys=ON");
			statement.execute("CREATE TEMP TABLE parent(id INT PRIMARY KEY)");
			statement.execute("CREATE TEMP TABLE child(id INT REFERENCES parent(id) DEFERRABLE INITIALLY DEFERRED)");
			connection.setAutoCommit(false);
			statement.execute("INSERT INTO temp.child VALUES(99)");
			var builder = new SqliteForeignKeyCheckBuilder(DialectHolder.defaultDialect).schema("temp");
			try (var result = statement.executeQuery(builder.build())) {
				assertTrue(result.next()); assertEquals("child", result.getString("table")); assertFalse(result.next());
			}
			assertFalse(connection.getAutoCommit());
			try (var result = statement.executeQuery("PRAGMA foreign_keys")) {
				assertTrue(result.next()); assertEquals(1, result.getInt(1));
			}
			connection.rollback();
			try (var result = statement.executeQuery(builder.build())) { assertFalse(result.next()); }
		}
	}

	@Test
	void rejectsUnsupportedAndUnknownRevisionsAtTheExactBoundary() {
		var resolver = DialectResolver.getInstance();
		for (Integer revision : new Integer[] {null,0,15}) {
			var builder = new SqliteForeignKeyCheckBuilder(resolver.getDialect("SQLite",3,7,revision));
			assertThrows(IllegalArgumentException.class, builder::build);
		}
		for (int[] version : new int[][] {{3,7,16},{3,7,17},{3,8,0},{3,37,0}}) {
			var builder = new SqliteForeignKeyCheckBuilder(resolver.getDialect("SQLite",version[0],version[1],version[2]));
			assertEquals(new SqliteForeignKeyCheckBuilder(DialectHolder.defaultDialect).build(), builder.build());
		}
	}
}
