/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;
import static com.sqlapp.data.db.dialect.sqlite.sql.SqliteBatchRebuildTest.*;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.sqlapp.data.schemas.Index;

class SqliteVirtualTableRebuildTest {
	static Stream<Arguments> cases() {
		return Stream.of("main", "temp", "other\" db")
				.flatMap(database -> Stream.of("fts5", "external", "rtree", "rtree_i32").flatMap(module -> Stream
						.of("success", "view", "index").map(outcome -> Arguments.of(database, module, outcome))));
	}

	@ParameterizedTest
	@MethodSource("cases")
	void reconstructsVirtualIndexesAndDependenciesOrRollsBack(String database, String module, String outcome)
			throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS \"other\"\" db\"");
			String prefix = quote(database) + ".";
			boolean fts = module.equals("fts5") || module.equals("external");
			statement.execute("CREATE TABLE " + prefix + "docs(docid INTEGER PRIMARY KEY,value TEXT)");
			statement.execute("INSERT INTO " + prefix + "docs VALUES(5,'alpha beta'),(80,'gamma')");
			statement.execute("CREATE TABLE " + prefix + "audit(value INT)");
			statement.execute("CREATE INDEX " + prefix + "collision ON audit(value)");
			String source = fts
					? "fts5(value" + (module.equals("external") ? ",content='docs',content_rowid='docid'" : "") + ")"
					: module + "(id,minX,maxX,+label)";
			statement.execute("CREATE VIRTUAL TABLE " + prefix + "t USING " + source);
			if (fts) {
				if (module.equals("external"))
					statement.execute("INSERT INTO " + prefix + "t(t) VALUES('rebuild')");
				else
					statement.execute(
							"INSERT INTO " + prefix + "t(rowid,value) SELECT docid,value FROM " + prefix + "docs");
				statement.execute("INSERT INTO " + prefix + "t(t,rank) VALUES('automerge',4)");
				statement.execute("INSERT INTO " + prefix + "t(t,rank) VALUES('rank','bm25(7.0)')");
			} else
				statement.execute("INSERT INTO " + prefix + "t VALUES(5,1,9,'first'),(80,10,20,'second')");
			statement.execute("CREATE VIEW " + prefix + "v AS SELECT "
					+ (fts ? "rowid AS id,value" : "id,label AS value") + " FROM t");
			String nativeTarget = fts
					? "fts5(\"new value\",extra UNINDEXED"
							+ (module.equals("external") ? ",content='docs',content_rowid='docid'" : "") + ")"
					: module + "(\"new id\",minX,maxX,+label,+extra)";
			statement.execute("CREATE VIRTUAL TABLE " + prefix + "template USING " + nativeTarget);
			var target = read(connection, database).getTables().get("template").clone();
			target.setName("t");
			statement.execute("DROP TABLE " + prefix + "template");
			var desired = read(connection, database);
			desired.getTables().remove("t");
			desired.getTables().add(target);
			var docs = desired.getTables().get("docs");
			var options = new SqliteTableRebuilder.Options().tables("docs", "t");
			if (fts)
				options.sourceColumns("t", Map.of("new value", "value"));
			else
				options.sourceColumns("t", Map.of("new id", "id"));
			if (module.equals("external")) {
				docs.getColumns().get("value").setName("new value");
				// New FTS external columns must also exist in the final content table.
				docs.getColumns().add(new com.sqlapp.data.schemas.Column("extra")
						.setDataType(com.sqlapp.data.db.datatype.DataType.VARCHAR).setDataTypeName("TEXT"));
				options.sourceColumns("docs", Map.of("new value", "value"));
			}
			desired.getViews().get("v").setStatement("SELECT "
					+ (fts ? "rowid AS id,\"new value\" AS value" : "\"new id\" AS id,label AS value") + " FROM t");
			if (outcome.equals("view"))
				desired.getViews().get("v").setStatement("SELECT absent FROM t");
			if (outcome.equals("index")) {
				var index = new Index("collision");
				index.getColumns().add(docs.getColumns().get(0));
				docs.getIndexes().add(index);
			}
			var before = catalog(connection, database);
			String xml = desired.asXml();
			boolean success = outcome.equals("success");
			if (success)
				assertEquals(Map.of("docs", 2L, "t", 2L),
						new SqliteTableRebuilder().rebuild(connection, desired, options).rowCounts());
			else {
				assertThrows(SQLException.class,
						() -> new SqliteTableRebuilder().rebuild(connection, desired, options));
				assertEquals(before, catalog(connection, database));
			}
			assertEquals(xml, desired.asXml());
			assertTrue(connection.getAutoCommit());
			try (var rows = statement.executeQuery("SELECT id,value FROM " + prefix + "v ORDER BY id")) {
				assertTrue(rows.next());
				assertEquals(5, rows.getInt(1));
				assertEquals(fts ? "alpha beta" : "first", rows.getString(2));
				assertTrue(rows.next());
				assertEquals(80, rows.getInt(1));
				assertFalse(rows.next());
			}
			if (fts) {
				try (var rows = statement.executeQuery("SELECT rowid FROM " + prefix + "t WHERE t MATCH 'alpha'")) {
					assertTrue(rows.next());
					assertEquals(5, rows.getInt(1));
					assertFalse(rows.next());
				}
				try (var rows = statement.executeQuery("SELECT v FROM " + prefix + "t_config WHERE k='rank'")) {
					assertTrue(rows.next());
					assertEquals("bm25(7.0)", rows.getString(1));
				}
				try (var rows = statement.executeQuery("SELECT v FROM " + prefix + "t_config WHERE k='automerge'")) {
					assertTrue(rows.next());
					assertEquals(4, rows.getInt(1));
				}
				statement.execute("INSERT INTO " + prefix + "t(t,rank) VALUES('integrity-check',1)");
			} else {
				try (var rows = statement
						.executeQuery("SELECT rowid,label FROM " + prefix + "t WHERE minX<=2 AND maxX>=2")) {
					assertTrue(rows.next());
					assertEquals(5, rows.getInt(1));
					assertEquals("first", rows.getString(2));
					assertFalse(rows.next());
				}
				try (var rows = statement
						.executeQuery("SELECT rtreecheck('" + database.replace("'", "''") + "','t')")) {
					assertTrue(rows.next());
					assertEquals("ok", rows.getString(1));
				}
			}
		}
	}

	@Test
	void rejectsContentlessUnknownModulesAndInconsistentModelsWithoutLosingTheIndex() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE VIRTUAL TABLE t USING fts5(value,content='')");
			statement.execute("INSERT INTO t(rowid,value) VALUES(40,'alpha')");
			var desired = read(connection, "main");
			var before = catalog(connection, "main");
			assertThrows(IllegalArgumentException.class, () -> new SqliteTableRebuilder().rebuild(connection, desired));
			assertEquals(before, catalog(connection, "main"));
			try (var rows = statement.executeQuery("SELECT rowid FROM t WHERE t MATCH 'alpha'")) {
				assertTrue(rows.next());
				assertEquals(40, rows.getInt(1));
			}
			statement.execute("DROP TABLE t");
			statement.execute("CREATE VIRTUAL TABLE base USING fts5(value)");
			statement.execute("CREATE VIRTUAL TABLE t USING fts5vocab(base,'row')");
			var old = read(connection, "main");
			assertThrows(IllegalArgumentException.class, () -> new SqliteTableRebuilder().rebuild(connection, old));
			statement.execute("DROP TABLE t");
			statement.execute("DROP TABLE base");
			statement.execute("CREATE VIRTUAL TABLE t USING fts5(value)");
			var mismatched = read(connection, "main");
			mismatched.getTables().get("t").getColumns().get("value").setName("renamed");
			assertThrows(IllegalArgumentException.class,
					() -> new SqliteTableRebuilder().rebuild(connection, mismatched));
		}
	}

	@Test
	void rejectsStaleExternalFtsBeforeReconstructingContent() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE docs(id INTEGER PRIMARY KEY,value TEXT)");
			statement.execute("INSERT INTO docs VALUES(40,'alpha')");
			statement.execute("CREATE VIRTUAL TABLE t USING fts5(value,content='docs',content_rowid='id')");
			statement.execute("INSERT INTO t(t) VALUES('rebuild')");
			statement.execute("UPDATE docs SET value='beta'");
			var desired = read(connection, "main");
			var before = catalog(connection, "main");
			assertThrows(SQLException.class, () -> new SqliteTableRebuilder().rebuild(connection, desired));
			assertEquals(before, catalog(connection, "main"));
			try (var rows = statement.executeQuery("SELECT value FROM docs")) {
				assertTrue(rows.next());
				assertEquals("beta", rows.getString(1));
			}
			assertTrue(connection.getAutoCommit());
		}
	}
}
