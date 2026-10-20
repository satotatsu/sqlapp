/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.dialect.sqlite.sql.SqliteWalCheckpointBuilder.Mode;
import com.sqlapp.data.schemas.Schema;

class SqliteWalCheckpointBuilderTest {
	@TempDir Path directory;

	@Test
	void allModesCheckpointAFileDatabaseAndTruncateShrinksTheWal() throws Exception {
		Path file = directory.resolve("database.db");
		try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file)) {
			execute(connection,"PRAGMA journal_mode=WAL");
			execute(connection,"PRAGMA wal_autocheckpoint=0");
			execute(connection,"CREATE TABLE t(id INTEGER PRIMARY KEY)");
			var builder = new SqliteWalCheckpointBuilder(DialectHolder.defaultDialect);
			assertTrue(builder.build().endsWith("wal_checkpoint(PASSIVE)"));
			for (Mode mode : Mode.values()) {
				execute(connection,"INSERT INTO t DEFAULT VALUES");
				assertTrue(Files.size(Path.of(file + "-wal")) > 0);
				int[] status = checkpoint(connection,builder.mode(mode).build());
				assertEquals(0,status[0]); assertTrue(status[1]>=0); assertEquals(status[1],status[2]);
				if (mode == Mode.TRUNCATE) assertEquals(0,Files.size(Path.of(file + "-wal")));
			}
			try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT count(*) FROM t")) {
				assertTrue(result.next()); assertEquals(4,result.getInt(1));
			}
		}
	}

	@Test
	void attachedSchemaTruncationDoesNotCheckpointMain() throws Exception {
		Path main = directory.resolve("main.db");
		Path other = directory.resolve("other.db");
		try (var connection = DriverManager.getConnection("jdbc:sqlite:" + main)) {
			try (var attach = connection.prepareStatement("ATTACH ? AS \"Other]Db\"")) {
				attach.setString(1,other.toString()); attach.execute();
			}
			execute(connection,"PRAGMA wal_autocheckpoint=0");
			for (String schema : new String[] {"main","\"Other]Db\""}) {
				execute(connection,"PRAGMA " + schema + ".journal_mode=WAL");
				execute(connection,"CREATE TABLE " + schema + ".t(id INT)");
				execute(connection,"INSERT INTO " + schema + ".t VALUES(7)");
			}
			long mainSize = Files.size(Path.of(main + "-wal"));
			assertTrue(mainSize>0); assertTrue(Files.size(Path.of(other + "-wal"))>0);
			var builder = new SqliteWalCheckpointBuilder(DialectHolder.defaultDialect)
					.schema(new Schema("Other]Db")).mode(Mode.TRUNCATE);
			assertEquals(0,checkpoint(connection,builder.build())[0]);
			assertEquals(0,Files.size(Path.of(other + "-wal")));
			assertEquals(mainSize,Files.size(Path.of(main + "-wal")));
		}
	}

	@Test
	void busyReaderStatusIsReturnedAndCanBeRetriedAfterTheReaderFinishes() throws Exception {
		Path file = directory.resolve("busy.db");
		try (var writer = DriverManager.getConnection("jdbc:sqlite:" + file)) {
			execute(writer,"PRAGMA journal_mode=WAL"); execute(writer,"PRAGMA wal_autocheckpoint=0");
			execute(writer,"PRAGMA busy_timeout=0"); execute(writer,"CREATE TABLE t(id INT)");
			execute(writer,"INSERT INTO t VALUES(1)");
			var builder = new SqliteWalCheckpointBuilder(DialectHolder.defaultDialect).mode(Mode.TRUNCATE);
			try (var reader = DriverManager.getConnection("jdbc:sqlite:" + file)) {
				reader.setAutoCommit(false);
				try (var statement = reader.createStatement(); var result = statement.executeQuery("SELECT * FROM t")) {
					assertTrue(result.next());
					execute(writer,"INSERT INTO t VALUES(2)");
					int[] passive = checkpoint(writer,builder.mode(Mode.PASSIVE).build());
					assertEquals(0,passive[0]); assertTrue(passive[1]>passive[2]);
					builder.mode(Mode.TRUNCATE);
					int[] status = checkpoint(writer,builder.build());
					assertEquals(1,status[0]); assertTrue(status[1]>status[2]);
					assertFalse(reader.getAutoCommit()); assertEquals(1,result.getInt(1));
				}
				reader.rollback();
			}
			assertArrayEquals(new int[] {0,0,0},checkpoint(writer,builder.build()));
		}
	}

	@Test
	void nonWalCountsAndInvalidSchemaErrorsRemainVisible() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			var builder = new SqliteWalCheckpointBuilder(DialectHolder.defaultDialect);
			assertArrayEquals(new int[] {0,-1,-1},checkpoint(connection,builder.build()));
			execute(connection,"CREATE TABLE kept(id INT)");
			assertThrows(SQLException.class,() -> checkpoint(connection,builder.schema("missing; DROP TABLE kept; --").build()));
			try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT count(*) FROM kept")) {
				assertTrue(result.next()); assertEquals(0,result.getInt(1));
			}
		}
	}

	@Test
	void rejectsInvalidOptionsAndHonorsBothModeBoundaries() {
		var resolver = DialectResolver.getInstance();
		var builder = new SqliteWalCheckpointBuilder(DialectHolder.defaultDialect);
		for (String value : new String[] {null," ","bad\0schema"})
			assertThrows(IllegalArgumentException.class,() -> builder.schema(value));
		assertThrows(NullPointerException.class,() -> builder.mode(null));
		assertThrows(NullPointerException.class,() -> new SqliteWalCheckpointBuilder(null));
		assertThrows(IllegalArgumentException.class,() -> new SqliteWalCheckpointBuilder(resolver.getDialect("PostgreSQL",15,0,0)));
		for (Integer revision : new Integer[] {null,0,5}) {
			var old = new SqliteWalCheckpointBuilder(resolver.getDialect("SQLite",3,7,revision));
			assertThrows(IllegalArgumentException.class,old::build);
		}
		var first = new SqliteWalCheckpointBuilder(resolver.getDialect("SQLite",3,7,6));
		for (Mode mode : new Mode[] {Mode.PASSIVE,Mode.FULL,Mode.RESTART})
			assertEquals(builder.mode(mode).build(),first.mode(mode).build());
		var beforeTruncate = new SqliteWalCheckpointBuilder(resolver.getDialect("SQLite",3,8,7)).mode(Mode.TRUNCATE);
		assertThrows(IllegalArgumentException.class,beforeTruncate::build);
		var firstTruncate = new SqliteWalCheckpointBuilder(resolver.getDialect("SQLite",3,8,8)).mode(Mode.TRUNCATE);
		assertEquals(builder.mode(Mode.TRUNCATE).build(),firstTruncate.build());
	}

	private void execute(Connection connection, String sql) throws SQLException {
		try (var statement = connection.createStatement()) { statement.execute(sql); }
	}

	private int[] checkpoint(Connection connection, String sql) throws SQLException {
		try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
			assertEquals(3,result.getMetaData().getColumnCount()); assertTrue(result.next());
			int[] status = {result.getInt(1),result.getInt(2),result.getInt(3)};
			assertFalse(result.next()); return status;
		}
	}
}
