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
import org.junit.jupiter.params.provider.CsvSource;

import com.sqlapp.data.schemas.Table;

class SqliteContentRecoveryTest {
	@ParameterizedTest
	@CsvSource({"main,false","temp,false","other\" db,false","main,true","temp,true","other\" db,true"})
	void mapsAuthoritativeColumnsIndependentlyOfDestinationColumns(String database, boolean view) throws Exception {
		try(var connection=DriverManager.getConnection("jdbc:sqlite::memory:");var statement=connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS \"other\"\" db\"");String prefix=quote(database)+".";
			statement.execute("CREATE TABLE "+prefix+"docs(id INTEGER PRIMARY KEY,\"source\"\" body\" TEXT,note)");
			statement.execute("INSERT INTO "+prefix+"docs VALUES(-40,'alpha beta',x'00FF'),(80,'',NULL)");
			if(view) statement.execute("CREATE VIEW "+prefix+"content AS SELECT * FROM docs");
			statement.execute("CREATE VIRTUAL TABLE "+prefix+"t USING fts5(body,note UNINDEXED,content='')");
			statement.execute("INSERT INTO "+prefix+"t(rowid,body,note) SELECT id,\"source\"\" body\",note FROM "+prefix+"docs");
			statement.execute("CREATE VIRTUAL TABLE "+prefix+"template USING fts5(\"destination body\",note UNINDEXED)");
			var target=read(connection,database).getTables().get("template").clone();target.setName("t");statement.execute("DROP TABLE "+prefix+"template");
			var desired=read(connection,database);desired.getTables().remove("t");desired.getTables().add(target);String xml=desired.asXml();
			var mapping=new java.util.LinkedHashMap<String,String>();mapping.put("body","source\" body");
			var options=new SqliteTableRebuilder.Options().tables("t").sourceColumns("t",Map.of("destination body","body"))
					.recoverContent("t",new Table(view?"content":"docs"),view?"id":null,mapping);
			mapping.put("body","absent"); // Options retain their own snapshot.
			assertEquals(2L,new SqliteTableRebuilder().rebuild(connection,desired,options).rowCounts().get("t"));
			try(var rows=statement.executeQuery("SELECT rowid,\"destination body\",note FROM "+prefix+"t WHERE t MATCH 'alpha'")) {
				assertTrue(rows.next());assertEquals(-40,rows.getLong(1));assertEquals("alpha beta",rows.getString(2));assertArrayEquals(new byte[]{0,(byte)255},rows.getBytes(3));assertFalse(rows.next());
			}
			assertEquals(xml,desired.asXml());
			try(var rows=statement.executeQuery("SELECT count(*) FROM temp.sqlite_master WHERE name LIKE 'sqlapp_%'")) {assertTrue(rows.next());assertEquals(0,rows.getInt(1));}
		}
	}

	@ParameterizedTest
	@CsvSource({"unknown","hidden","missing","duplicate","terms","blank"})
	void rejectsInvalidRecoveryColumnMappingsWithoutChangingTheDatabase(String outcome) throws Exception {
		try(var connection=DriverManager.getConnection("jdbc:sqlite::memory:");var statement=connection.createStatement()) {
			statement.execute("CREATE TABLE docs(id INTEGER PRIMARY KEY,body TEXT,wrong TEXT)");statement.execute("INSERT INTO docs VALUES(40,'alpha','different')");
			statement.execute("CREATE VIRTUAL TABLE t USING fts5(body,content='')");statement.execute("INSERT INTO t(rowid,body) VALUES(40,'alpha')");
			var desired=read(connection,"main");var before=catalog(connection,"main");
			Map<String,String> mappings=switch(outcome) {
				case "unknown" -> Map.of("absent","body");case "hidden" -> Map.of("rank","body");
				case "missing" -> Map.of("body","absent");case "duplicate" -> Map.of("body","body","BODY","wrong");
				case "terms" -> Map.of("body","wrong");default -> Map.of("body"," ");
			};
			Class<? extends Exception> failure=outcome.equals("terms")?SQLException.class:IllegalArgumentException.class;
			assertThrows(failure,()->new SqliteTableRebuilder().rebuild(connection,desired,new SqliteTableRebuilder.Options().tables("t").recoverContent("t",new Table("docs"),null,mappings)));
			assertEquals(before,catalog(connection,"main"));assertTrue(connection.getAutoCommit());
			try(var rows=statement.executeQuery("SELECT rowid FROM t WHERE t MATCH 'alpha'")) {assertTrue(rows.next());assertEquals(40,rows.getLong(1));}
			try(var rows=statement.executeQuery("SELECT count(*) FROM temp.sqlite_master WHERE name LIKE 'sqlapp_%'")) {assertTrue(rows.next());assertEquals(0,rows.getInt(1));}
		}
	}

	@Test
	void materializesAnOwningViewBeforeReplacingItAndItsBackingColumns() throws Exception {
		try(var connection=DriverManager.getConnection("jdbc:sqlite::memory:");var statement=connection.createStatement()) {
			statement.execute("CREATE TABLE docs(k INTEGER PRIMARY KEY,body TEXT)");statement.execute("INSERT INTO docs VALUES(40,'alpha beta')");
			statement.execute("CREATE VIEW content AS SELECT k AS doc_id,body FROM docs");
			statement.execute("CREATE VIRTUAL TABLE t USING fts5(body,content='')");statement.execute("INSERT INTO t(rowid,body) VALUES(40,'alpha beta')");
			statement.execute("CREATE VIRTUAL TABLE template USING fts5(body)");
			var target=read(connection,"main").getTables().get("template").clone();target.setName("t");statement.execute("DROP TABLE template");
			var desired=read(connection,"main");desired.getTables().remove("t");desired.getTables().add(target);desired.getTables().get("docs").getColumns().get("body").setName("new body");
			desired.getViews().get("content").setStatement("SELECT k AS doc_id,\"new body\" AS body FROM docs");
			var options=new SqliteTableRebuilder.Options().sourceColumns("docs",Map.of("new body","body")).recoverContent("t",new Table("content"),"doc_id");
			new SqliteTableRebuilder().rebuild(connection,desired,options);
			try(var rows=statement.executeQuery("SELECT body FROM t WHERE t MATCH 'alpha'")) {assertTrue(rows.next());assertEquals("alpha beta",rows.getString(1));}
			try(var rows=statement.executeQuery("SELECT body FROM content")) {assertTrue(rows.next());assertEquals("alpha beta",rows.getString(1));}
		}
	}

	@ParameterizedTest
	@CsvSource({"main,false","temp,false","other\" db,false","main,true","temp,true","other\" db,true"})
	void createsContentlessDestinationsFromRecoverableContent(String database,boolean deletable) throws Exception {
		try(var connection=DriverManager.getConnection("jdbc:sqlite::memory:");var statement=connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS \"other\"\" db\"");String prefix=quote(database)+".";
			statement.execute("CREATE VIRTUAL TABLE t USING fts5(body)"); // Unselected same-name main table.
			if(!database.equals("main")) statement.execute("CREATE VIRTUAL TABLE "+prefix+"t USING fts5(body)");
			statement.execute("INSERT INTO "+prefix+"t(rowid,body) VALUES(-40,'alpha beta'),(80,'')");
			statement.execute("CREATE VIRTUAL TABLE "+prefix+"template USING fts5(body,content=''"+(deletable?",contentless_delete=1":"")+")");
			var target=read(connection,database).getTables().get("template").clone();target.setName("t");statement.execute("DROP TABLE "+prefix+"template");
			var desired=read(connection,database);desired.getTables().remove("t");desired.getTables().add(target);
			assertEquals(2L,new SqliteTableRebuilder().rebuild(connection,desired).rowCounts().get("t"));
			try(var rows=statement.executeQuery("SELECT rowid,body FROM "+prefix+"t WHERE t MATCH 'alpha'")) {assertTrue(rows.next());assertEquals(-40,rows.getLong(1));assertNull(rows.getString(2));}
			try(var rows=statement.executeQuery("SELECT count(*) FROM "+prefix+"t")) {assertTrue(rows.next());assertEquals(2,rows.getInt(1));}
		}
	}

	@ParameterizedTest
	@CsvSource({"duplicate","null","text","extra","positions","missingColumn","journal","columnsize","without","authority","module"})
	void checksRecoveryBoundaries(String outcome) throws Exception {
		try(var connection=DriverManager.getConnection("jdbc:sqlite::memory:");var statement=connection.createStatement()) {
			String sourceDdl=outcome.equals("without")?"(k INTEGER PRIMARY KEY,body TEXT) WITHOUT ROWID":"(k,body TEXT)";
			statement.execute("CREATE TABLE docs"+sourceDdl);statement.execute("INSERT INTO docs VALUES(40,'alpha beta')");
			String stored=outcome.equals("columnsize")?",columnsize=0":"";
			statement.execute("CREATE VIRTUAL TABLE t USING fts5(body,content=''"+stored+")");statement.execute("INSERT INTO t(rowid,body) VALUES(40,'alpha beta')");
			if(outcome.equals("duplicate")) statement.execute("INSERT INTO docs VALUES(40,'alpha beta')");
			if(outcome.equals("null")) statement.execute("UPDATE docs SET k=NULL");
			if(outcome.equals("text")) statement.execute("UPDATE docs SET k='40'");
			if(outcome.equals("extra")) statement.execute("INSERT INTO docs VALUES(80,'')");
			if(outcome.equals("positions")) statement.execute("UPDATE docs SET body='beta alpha'");
			if(outcome.equals("authority")) statement.execute("UPDATE docs SET body='ALPHA   beta'");
			if(outcome.equals("missingColumn")) {statement.execute("DROP TABLE docs");statement.execute("CREATE TABLE docs(k)");statement.execute("INSERT INTO docs VALUES(40)");}
			statement.execute("CREATE VIRTUAL TABLE template USING "+(outcome.equals("module")?"fts4":"fts5")+"(body)");
			var target=read(connection,"main").getTables().get("template").clone();target.setName("t");statement.execute("DROP TABLE template");
			var desired=read(connection,"main");desired.getTables().remove("t");desired.getTables().add(target);
			var options=new SqliteTableRebuilder.Options().tables("t");
			if(outcome.equals("without")) options.recoverContent("t",new Table("docs"));else options.recoverContent("t",new Table("docs"),"k");
			if(outcome.equals("module")) options.convertModule("t");
			if(outcome.equals("journal")) try(var mode=statement.executeQuery("PRAGMA temp.journal_mode=OFF")) {assertTrue(mode.next());assertEquals("off",mode.getString(1));}
			var before=catalog(connection,"main");
			if(outcome.equals("without")||outcome.equals("authority")||outcome.equals("module")) {
				new SqliteTableRebuilder().rebuild(connection,desired,options);
				try(var rows=statement.executeQuery("SELECT body FROM t WHERE t MATCH 'alpha'")) {assertTrue(rows.next());assertEquals(outcome.equals("authority")?"ALPHA   beta":"alpha beta",rows.getString(1));}
			} else {
				if(outcome.equals("missingColumn")||outcome.equals("journal")||outcome.equals("columnsize")) assertThrows(IllegalArgumentException.class,()->new SqliteTableRebuilder().rebuild(connection,desired,options));
				else assertThrows(SQLException.class,()->new SqliteTableRebuilder().rebuild(connection,desired,options));
				assertEquals(before,catalog(connection,"main"));
			}
			try(var rows=statement.executeQuery("SELECT count(*) FROM temp.sqlite_master WHERE name LIKE 'sqlapp_%'")) {assertTrue(rows.next());assertEquals(0,rows.getInt(1));}
			assertTrue(connection.getAutoCommit());
		}
	}
	@Test
	void storedUnindexedContentIsRejectedBeforeTheBundledNativeDropLimitation() throws Exception {
		try(var connection=DriverManager.getConnection("jdbc:sqlite::memory:");var statement=connection.createStatement()) {
			statement.execute("CREATE VIRTUAL TABLE t USING fts5(body,note UNINDEXED,content='',contentless_delete=1,contentless_unindexed=1)");
			statement.execute("INSERT INTO t(rowid,body,note) VALUES(40,'alpha','memo')");
			var desired=read(connection,"main");var before=catalog(connection,"main");
			var failure=assertThrows(IllegalArgumentException.class,()->new SqliteTableRebuilder().rebuild(connection,desired));
			assertTrue(failure.getMessage().contains("contentless_unindexed"));assertEquals(before,catalog(connection,"main"));
			statement.execute("CREATE VIRTUAL TABLE replacement USING fts5(body,note UNINDEXED)");
			statement.execute("INSERT INTO replacement(rowid,body,note) VALUES(40,'alpha','memo')");
			statement.execute("DROP TABLE t");
			// Reproduce the current driver's limitation independently of our executor.
			assertTrue(catalog(connection,"main").stream().anyMatch(row->row.startsWith("t_content|")));
			assertThrows(SQLException.class,()->statement.execute("ALTER TABLE replacement RENAME TO t"));
		}
	}
	static Stream<Arguments> cases() {
		return Stream.of("main","temp","other\" db").flatMap(database -> Stream.of("classic","delete","none","column")
				.flatMap(kind -> Stream.of("success","terms","count","ids","view","copy").map(outcome -> Arguments.of(database,kind,outcome))));
	}

	@ParameterizedTest
	@MethodSource("cases")
	void restoresVerifiedContentOrRollsBackEverything(String database,String kind,String outcome) throws Exception {
		try(var connection=DriverManager.getConnection("jdbc:sqlite::memory:");var statement=connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS \"other\"\" db\"");String prefix=quote(database)+".";
			statement.execute("CREATE TABLE "+prefix+"docs(id INTEGER PRIMARY KEY,body TEXT,note)");
			statement.execute("INSERT INTO "+prefix+"docs VALUES(-40,'alpha beta',x'00FF'),(80,'',NULL),(9007199254740993,'alpha alpha gamma','memo')");
			String extra=switch(kind) {case "delete" -> ",contentless_delete=1";case "unindexed" -> ",contentless_delete=1,contentless_unindexed=1";case "none" -> ",detail=none";case "column" -> ",detail=column";default -> "";};
			statement.execute("CREATE VIRTUAL TABLE "+prefix+"t USING fts5(body,note UNINDEXED,content=''"+extra+")");
			statement.execute("INSERT INTO "+prefix+"t(rowid,body,note) SELECT id,body,note FROM "+prefix+"docs");
			if(kind.equals("delete")||kind.equals("unindexed")) {statement.execute("INSERT INTO "+prefix+"t(rowid,body) VALUES(1000,'deleted')");statement.execute("DELETE FROM "+prefix+"t WHERE rowid=1000");}
			statement.execute("INSERT INTO "+prefix+"t(t,rank) VALUES('automerge',8)");
			statement.execute("CREATE VIEW "+prefix+"v AS SELECT rowid AS id,body FROM t");
			statement.execute("CREATE VIRTUAL TABLE "+prefix+"template USING fts5(\"new body\",note UNINDEXED)");
			var target=read(connection,database).getTables().get("template").clone();target.setName("t");statement.execute("DROP TABLE "+prefix+"template");
			var desired=read(connection,database);desired.getTables().remove("t");desired.getTables().add(target);
			desired.getViews().get("v").setStatement(outcome.equals("view")?"SELECT absent FROM t":"SELECT rowid AS id,\"new body\" AS body FROM t");
			if(outcome.equals("terms")) statement.execute("UPDATE "+prefix+"docs SET body='different' WHERE id=-40");
			if(outcome.equals("count")) statement.execute("UPDATE "+prefix+"docs SET body='alpha gamma' WHERE id=9007199254740993");
			if(outcome.equals("ids")) statement.execute("DELETE FROM "+prefix+"docs WHERE id=80");
			if(outcome.equals("copy")) desired.getTables().get("docs").getConstraints().add(new com.sqlapp.data.schemas.CheckConstraint("reject","id<100"));
			var options=new SqliteTableRebuilder.Options().tables("docs","t").sourceColumns("t",Map.of("new body","body")).recoverContent("t",new Table("docs"));
			var before=catalog(connection,database);var temporary=catalog(connection,"temp");String xml=desired.asXml();boolean success=outcome.equals("success");
			if(success) {
				var result=new SqliteTableRebuilder().rebuild(connection,desired,options);assertEquals(Map.of("docs",3L,"t",3L),result.rowCounts());
				assertTrue(result.operations().stream().anyMatch(operation->operation.getSqlText().contains("fts5vocab")));
				try(var rows=statement.executeQuery("SELECT \"new body\",note FROM "+prefix+"t WHERE rowid=-40")) {assertTrue(rows.next());assertEquals("alpha beta",rows.getString(1));assertArrayEquals(new byte[]{0,(byte)255},rows.getBytes(2));}
				try(var rows=statement.executeQuery("SELECT \"new body\" FROM "+prefix+"t WHERE rowid=80")) {assertTrue(rows.next());assertEquals("",rows.getString(1));}
			} else {
				assertThrows(SQLException.class,()->new SqliteTableRebuilder().rebuild(connection,desired,options));
				assertEquals(before,catalog(connection,database));assertEquals(temporary,catalog(connection,"temp"));
			}
			try(var rows=statement.executeQuery("SELECT rowid FROM "+prefix+"t WHERE t MATCH 'alpha' ORDER BY rowid")) {assertTrue(rows.next());assertEquals(-40,rows.getLong(1));assertTrue(rows.next());assertEquals(9007199254740993L,rows.getLong(1));assertFalse(rows.next());}
			try(var rows=statement.executeQuery("SELECT v FROM "+prefix+"t_config WHERE k='automerge'")) {assertTrue(rows.next());assertEquals(8,rows.getInt(1));}
			for(String schema:new String[]{database,"temp"}) try(var rows=statement.executeQuery("SELECT count(*) FROM "+quote(schema)+".sqlite_master WHERE name LIKE 'sqlapp_%'")) {assertTrue(rows.next());assertEquals(0,rows.getInt(1));}
			assertEquals(xml,desired.asXml());assertTrue(connection.getAutoCommit());
		}
	}

	@Test
	void supportsRecoveryFromAViewInAnotherDatabaseWithExplicitIds() throws Exception {
		try(var connection=DriverManager.getConnection("jdbc:sqlite::memory:");var statement=connection.createStatement()) {
			statement.execute("CREATE TABLE docs(k INT,body TEXT)");statement.execute("INSERT INTO docs VALUES(40,'alpha')");
			statement.execute("CREATE VIEW content AS SELECT k AS doc_id,body FROM docs");
			statement.execute("CREATE VIRTUAL TABLE temp.t USING fts5(body,content='')");statement.execute("INSERT INTO temp.t(rowid,body) VALUES(40,'alpha')");
			var desired=read(connection,"temp");
			var source=new Table("content").setSchemaName("main");
			assertThrows(IllegalArgumentException.class,()->new SqliteTableRebuilder().rebuild(connection,desired,new SqliteTableRebuilder.Options().recoverContent("t",source)));
			new SqliteTableRebuilder().rebuild(connection,desired,new SqliteTableRebuilder.Options().recoverContent("t",source,"doc_id"));
			try(var rows=statement.executeQuery("SELECT rowid FROM temp.t WHERE t MATCH 'alpha'")) {assertTrue(rows.next());assertEquals(40,rows.getInt(1));}
		}
	}
}
