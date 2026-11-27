/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.schemas.Schema;

class SqliteVacuumIntoBuilderTest {
	@TempDir
	Path directory;

	@Test
	void copiesAnAttachedDatabaseAndItsExecutableSchemaWithoutChangingTheSource() throws Exception {
		Path destination = directory.resolve("snapshot's copy.db");
		try (var source = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = source.createStatement()) {
			statement.execute("ATTACH ':memory:' AS \"Other]Db\"");
			statement.execute("CREATE TABLE main.unrelated(id INT)");
			statement.execute("CREATE TABLE \"Other]Db\".t(id INTEGER PRIMARY KEY,value TEXT UNIQUE)");
			statement.execute("CREATE VIEW \"Other]Db\".v AS SELECT id,value FROM t");
			statement.execute(
					"CREATE TRIGGER \"Other]Db\".tr AFTER INSERT ON t BEGIN UPDATE t SET value=upper(value) WHERE id=new.id; END");
			statement.execute("INSERT INTO \"Other]Db\".t VALUES(1,'kept')");
			statement.close();
			var builder = new SqliteVacuumIntoBuilder(DialectHolder.defaultDialect).schema(new Schema("Other]Db"))
					.destination(destination.toString());
			assertFalse(Files.exists(destination));
			String sql = builder.build();
			assertFalse(Files.exists(destination));
			execute(source, sql);
			try (var copy = DriverManager.getConnection("jdbc:sqlite:" + destination);
					var query = copy.createStatement()) {
				try (var rows = query.executeQuery("SELECT value FROM v WHERE id=1")) {
					assertTrue(rows.next());
					assertEquals("KEPT", rows.getString(1));
				}
				query.execute("INSERT INTO t VALUES(2,'copied')");
				try (var rows = query.executeQuery("SELECT value FROM v WHERE id=2")) {
					assertTrue(rows.next());
					assertEquals("COPIED", rows.getString(1));
				}
				try (var rows = query.executeQuery("SELECT count(*) FROM sqlite_master WHERE name='unrelated'")) {
					assertTrue(rows.next());
					assertEquals(0, rows.getInt(1));
				}
				try (var rows = query.executeQuery("PRAGMA integrity_check")) {
					assertTrue(rows.next());
					assertEquals("ok", rows.getString(1));
				}
			}
			try (var query = source.createStatement();
					var rows = query.executeQuery("SELECT count(*) FROM \"Other]Db\".t")) {
				assertTrue(rows.next());
				assertEquals(1, rows.getInt(1));
			}
		}
	}

	@Test
	void defaultMainRejectsANonemptyDestinationAndAnOpenTransaction() throws Exception {
		Path destination = directory.resolve("main.db");
		try (var source = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = source.createStatement()) {
			statement.execute("CREATE TABLE t(id INT)");
			statement.execute("INSERT INTO t VALUES(1)");
			statement.close();
			var builder = new SqliteVacuumIntoBuilder(DialectHolder.defaultDialect).destination(destination.toString());
			execute(source, builder.build());
			assertThrows(SQLException.class, () -> execute(source, builder.build()));
			try (var copy = DriverManager.getConnection("jdbc:sqlite:" + destination);
					var query = copy.createStatement();
					var rows = query.executeQuery("SELECT id FROM t")) {
				assertTrue(rows.next());
				assertEquals(1, rows.getInt(1));
			}
			source.setAutoCommit(false);
			execute(source, "INSERT INTO t VALUES(2)");
			String transactionSql = builder.destination(directory.resolve("transaction.db").toString()).build();
			assertThrows(SQLException.class, () -> execute(source, transactionSql));
			assertFalse(source.getAutoCommit());
			source.rollback();
			try (var query = source.createStatement(); var rows = query.executeQuery("SELECT count(*) FROM t")) {
				assertTrue(rows.next());
				assertEquals(1, rows.getInt(1));
			}
		}
	}

	@Test
	void validatesOptionsAndRejectsOlderTargets() {
		var builder = new SqliteVacuumIntoBuilder(DialectHolder.defaultDialect);
		assertThrows(IllegalArgumentException.class, builder::build);
		for (String invalid : new String[] { null, " ", "bad\0name" }) {
			assertThrows(IllegalArgumentException.class, () -> builder.destination(invalid));
			assertThrows(IllegalArgumentException.class, () -> builder.schema(invalid));
		}
		assertThrows(NullPointerException.class, () -> new SqliteVacuumIntoBuilder(null));
		assertThrows(IllegalArgumentException.class,
				() -> new SqliteVacuumIntoBuilder(DialectResolver.getInstance().getDialect("PostgreSQL", 15, 0, 0)));
		var old = new SqliteVacuumIntoBuilder(DialectResolver.getInstance().getDialect("SQLite", 3, 26, 0))
				.destination("copy.db");
		assertThrows(IllegalArgumentException.class, old::build);
		assertEquals(builder.destination("copy.db").build(),
				new SqliteVacuumIntoBuilder(DialectResolver.getInstance().getDialect("SQLite", 3, 27, 0))
						.destination("copy.db").build());
	}

	private void execute(java.sql.Connection connection, String sql) throws SQLException {
		try (var statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}
}
