/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.dialect.sqlite.Sqlite;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

class SqliteMaintenanceTest {
	@Test
	void analyzeUsesNativeSyntaxAndKeepsAttachedIdentity() throws Exception {
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS \"Mixed]Db\"");
			for (String schema : List.of("main", "temp", "\"Mixed]Db\"")) {
				statement.execute("CREATE TABLE " + schema + ".t(id INT PRIMARY KEY,value TEXT)");
				statement.execute("CREATE INDEX " + schema + ".idx ON t(value)");
				statement.execute("INSERT INTO " + schema + ".t VALUES(1,'one'),(2,'two')");
			}
			var table = new Table("t").setSchemaName("Mixed]Db");
			String sql = registry.createSql(table, SqlType.ANALYZE).get(0).getSqlText();
			assertFalse(sql.contains("ANALYZE TABLE"));
			statement.execute(sql);
			try (var result = statement.executeQuery("SELECT stat FROM \"Mixed]Db\".sqlite_stat1 WHERE idx='idx'")) {
				assertTrue(result.next());
				assertTrue(result.getString(1).startsWith("2 "));
			}
			try (var result = statement
					.executeQuery("SELECT count(*) FROM main.sqlite_master WHERE name='sqlite_stat1'")) {
				assertTrue(result.next());
				assertEquals(0, result.getInt(1));
			}
			var index = new Index("idx").setSchemaName("Mixed]Db");
			statement.execute(registry.createSql(index, SqlType.ANALYZE).get(0).getSqlText());
			statement.execute(registry.createSql(new Schema("temp"), SqlType.ANALYZE).get(0).getSqlText());
			try (var result = statement.executeQuery("SELECT count(*) FROM temp.sqlite_stat1")) {
				assertTrue(result.next());
				assertTrue(result.getInt(1) > 0);
			}
			assertThrows(SQLException.class, () -> statement.execute(registry
					.createSql(new Table("missing").setSchemaName("main"), SqlType.ANALYZE).get(0).getSqlText()));
		}
	}

	@Test
	void vacuumHonorsVersionBoundariesAndOperationTypes() throws Exception {
		var old = (Sqlite) DialectResolver.getInstance().getDialect("SQLite", 3, 14, 0);
		var current = (Sqlite) DialectResolver.getInstance().getDialect("SQLite", 3, 15, 0);
		assertFalse(old.supportsVacuumSchema());
		assertTrue(current.supportsVacuumSchema());
		for (SqlType type : List.of(SqlType.DEFRAG, SqlType.DEFRAG_FULL)) {
			assertEquals("VACUUM",
					old.createSqlFactoryRegistry().createSql(new Schema("main"), type).get(0).getSqlText());
			assertThrows(IllegalArgumentException.class,
					() -> old.createSqlFactoryRegistry().createSql(new Schema("other"), type));
			var operation = current.createSqlFactoryRegistry().createSql(new Schema("other"), type).get(0);
			assertTrue(operation.getSqlText().contains("other"));
			assertEquals(type, operation.getSqlType());
		}
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS \"Other]Db\"");
			statement.execute("CREATE TABLE \"Other]Db\".t(id INTEGER PRIMARY KEY,value TEXT)");
			statement.execute("INSERT INTO \"Other]Db\".t VALUES(7,'kept')");
			statement.close();
			for (SqlType type : List.of(SqlType.DEFRAG, SqlType.DEFRAG_FULL)) {
				try (var maintenance = connection.createStatement()) {
					maintenance.execute(current.createSqlFactoryRegistry().createSql(new Schema("Other]Db"), type)
							.get(0).getSqlText());
				}
			}
			try (var query = connection.createStatement();
					var result = query.executeQuery("SELECT value FROM \"Other]Db\".t WHERE id=7")) {
				assertTrue(result.next());
				assertEquals("kept", result.getString(1));
			}
			connection.setAutoCommit(false);
			try (var transaction = connection.createStatement()) {
				transaction.execute("INSERT INTO \"Other]Db\".t VALUES(8,'pending')");
				assertThrows(SQLException.class, () -> transaction.execute(current.createSqlFactoryRegistry()
						.createSql(new Schema("Other]Db"), SqlType.DEFRAG).get(0).getSqlText()));
			}
			assertFalse(connection.getAutoCommit());
			connection.rollback();
		}
	}

	@Test
	void invalidOrWiderMaintenanceRequestsFailClearly() {
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(new Table("t"), SqlType.ANALYZE));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(new Index("idx"), SqlType.ANALYZE));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(new Schema(""), SqlType.ANALYZE));
		for (SqlType type : List.of(SqlType.DEFRAG, SqlType.DEFRAG_FULL)) {
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(new Schema(""), type));
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(new Table("t").setSchemaName("main"), type));
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(new Index("idx").setSchemaName("main"), type));
		}
	}
}
