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

class SqliteModuleConversionTest {
	@Test
	void requiresExplicitResetOfUnrepresentableSettings() throws Exception {
		try(var connection=DriverManager.getConnection("jdbc:sqlite::memory:");var statement=connection.createStatement()) {
			statement.execute("CREATE VIRTUAL TABLE t USING fts5(value)"); statement.execute("INSERT INTO t(rowid,value) VALUES(40,'alpha')");
			statement.execute("INSERT INTO t(t,rank) VALUES('rank','bm25(7.0)')");statement.execute("INSERT INTO t(t,rank) VALUES('automerge',8)");
			statement.execute("CREATE VIRTUAL TABLE template USING fts4(value)");
			var target=read(connection,"main").getTables().get("template").clone();target.setName("t");statement.execute("DROP TABLE template");
			var desired=read(connection,"main");desired.getTables().remove("t");desired.getTables().add(target);var before=catalog(connection,"main");
			var preserve=new SqliteTableRebuilder.Options().convertModule("t");
			var failure=assertThrows(IllegalArgumentException.class,()->new SqliteTableRebuilder().rebuild(connection,desired,preserve));
			assertTrue(failure.getMessage().contains("DESTINATION_DEFAULTS"));assertEquals(before,catalog(connection,"main"));
			try(var rows=statement.executeQuery("SELECT v FROM t_config WHERE k='rank'")) { assertTrue(rows.next());assertEquals("bm25(7.0)",rows.getString(1)); }
			var reset=new SqliteTableRebuilder.Options().convertModule("t",SqliteTableRebuilder.ModuleSettings.DESTINATION_DEFAULTS);
			new SqliteTableRebuilder().rebuild(connection,desired,reset);
			try(var rows=statement.executeQuery("SELECT rowid FROM t WHERE t MATCH 'alpha'")) {assertTrue(rows.next());assertEquals(40,rows.getInt(1));}
			try(var rows=statement.executeQuery("SELECT count(*) FROM t_stat WHERE id=2")) {assertTrue(rows.next());assertEquals(0,rows.getInt(1));}
		}
	}

	@Test
	void retainsZeroLanguageAndRefusesToFlattenNonzeroLanguagePartitions() throws Exception {
		try(var connection=DriverManager.getConnection("jdbc:sqlite::memory:");var statement=connection.createStatement()) {
			statement.execute("CREATE VIRTUAL TABLE t USING fts4(value,languageid='lid')");statement.execute("INSERT INTO t(rowid,value,lid) VALUES(40,'alpha',5)");
			statement.execute("CREATE VIRTUAL TABLE template USING fts5(value)");
			var target=read(connection,"main").getTables().get("template").clone();target.setName("t");statement.execute("DROP TABLE template");
			var desired=read(connection,"main");desired.getTables().remove("t");desired.getTables().add(target);var before=catalog(connection,"main");
			for(var policy:SqliteTableRebuilder.ModuleSettings.values()) {
				var options=new SqliteTableRebuilder.Options().convertModule("t",policy);
				var failure=assertThrows(IllegalArgumentException.class,()->new SqliteTableRebuilder().rebuild(connection,desired,options));
				assertTrue(failure.getMessage().contains("nonzero languageid"));assertEquals(before,catalog(connection,"main"));
			}
			statement.execute("DELETE FROM t WHERE rowid=40");statement.execute("INSERT INTO t(rowid,value,lid) VALUES(40,'alpha',0)");
			new SqliteTableRebuilder().rebuild(connection,desired,new SqliteTableRebuilder.Options().convertModule("t"));
			try(var rows=statement.executeQuery("SELECT rowid FROM t WHERE t MATCH 'alpha'")) {assertTrue(rows.next());assertEquals(40,rows.getInt(1));}
			try(var rows=statement.executeQuery("SELECT v FROM t_config WHERE k='automerge'")) {assertTrue(rows.next());assertEquals(0,rows.getInt(1));}
		}
	}

	@ParameterizedTest
	@CsvSource({"main,fts4,success","temp,fts4,success","other\" db,fts4,success","main,fts5,success","temp,fts5,success","other\" db,fts5,success",
		"main,fts4,view","temp,fts4,view","other\" db,fts4,view","main,fts5,view","temp,fts5,view","other\" db,fts5,view"})
	void convertsExternalContentAndItsBackingColumns(String database,String source,String outcome) throws Exception {
		try(var connection=DriverManager.getConnection("jdbc:sqlite::memory:");var statement=connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS \"other\"\" db\"");String prefix=quote(database)+".";String destination=source.equals("fts4")?"fts5":"fts4";
			statement.execute("CREATE TABLE "+prefix+"docs(id INTEGER PRIMARY KEY,value TEXT)");statement.execute("INSERT INTO "+prefix+"docs VALUES(40,'alpha')");
			String sourceOptions=source.equals("fts5")?",content_rowid='id'":"";
			String destinationOptions=destination.equals("fts5")?",content_rowid='id'":"";
			statement.execute("CREATE VIRTUAL TABLE "+prefix+"t USING "+source+"(value,content='docs'"+sourceOptions+")");statement.execute("INSERT INTO "+prefix+"t(t) VALUES('rebuild')");
			statement.execute("CREATE VIEW "+prefix+"v AS SELECT rowid AS id,value FROM t");
			statement.execute("CREATE TRIGGER "+prefix+"feed AFTER INSERT ON docs BEGIN INSERT INTO t(rowid,value) VALUES(new.id,new.value); END");
			statement.execute("CREATE VIRTUAL TABLE "+prefix+"template USING "+destination+"(\"new value\",content='docs'"+destinationOptions+")");
			var target=read(connection,database).getTables().get("template").clone();target.setName("t");statement.execute("DROP TABLE "+prefix+"template");
			var desired=read(connection,database);desired.getTables().remove("t");desired.getTables().add(target);
			desired.getTables().get("docs").getColumns().get("value").setName("new value");
			desired.getViews().get("v").setStatement(outcome.equals("view")?"SELECT absent FROM t":"SELECT rowid AS id,\"new value\" AS value FROM t");
			desired.getTriggers().get("feed").setDefinition("CREATE TRIGGER feed AFTER INSERT ON docs BEGIN INSERT INTO t(rowid,\"new value\") VALUES(new.id,new.\"new value\"); END");
			var options=new SqliteTableRebuilder.Options().tables("docs","t").sourceColumns("docs",Map.of("new value","value")).sourceColumns("t",Map.of("new value","value")).convertModule("t");
			var before=catalog(connection,database);boolean success=outcome.equals("success");
			if(success) new SqliteTableRebuilder().rebuild(connection,desired,options);
			else {assertThrows(SQLException.class,()->new SqliteTableRebuilder().rebuild(connection,desired,options));assertEquals(before,catalog(connection,database));}
			try(var rows=statement.executeQuery("SELECT id,value FROM "+prefix+"v")) {assertTrue(rows.next());assertEquals(40,rows.getInt(1));assertEquals("alpha",rows.getString(2));}
			boolean finalFts5=(success?destination:source).equals("fts5");
			statement.execute("INSERT INTO "+prefix+"docs VALUES(80,'beta')");
			statement.execute("INSERT INTO "+prefix+"t(t"+(finalFts5?",rank) VALUES('integrity-check',1)":") VALUES('integrity-check')"));
			try(var rows=statement.executeQuery("SELECT rowid FROM "+prefix+"t WHERE t MATCH 'beta'")) {assertTrue(rows.next());assertEquals(80,rows.getInt(1));}
			if(success) try(var rows=statement.executeQuery("SELECT "+(finalFts5?"v FROM "+prefix+"t_config WHERE k='automerge'":"value FROM "+prefix+"t_stat WHERE id=2"))) {assertTrue(rows.next());assertEquals(source.equals("fts4")?0:4,rows.getInt(1));}
		}
	}
	static Stream<Arguments> ftsCases() {
		return Stream.of("main", "temp", "other\" db").flatMap(database -> Stream.of("fts3", "fts4", "fts5")
				.flatMap(source -> Stream.of("fts3", "fts4", "fts5").filter(target -> !source.equals(target))
						.flatMap(target -> Stream.of("success", "view", "copy").map(outcome -> Arguments.of(database, source, target, outcome)))));
	}

	@ParameterizedTest
	@MethodSource("ftsCases")
	void convertsFtsFamiliesWithSettingsAndDependenciesAtomically(String database, String source, String destination, String outcome) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS \"other\"\" db\""); String prefix = quote(database) + ".";
			statement.execute("CREATE TABLE " + prefix + "docs(id INTEGER PRIMARY KEY,value TEXT)");
			statement.execute("INSERT INTO " + prefix + "docs VALUES(-40,'alpha café'),(80,'beta'),(9007199254740993,'gamma')");
			statement.execute("CREATE VIRTUAL TABLE " + prefix + "t USING " + source + "(value,skip)");
			statement.execute("INSERT INTO " + prefix + "t(rowid,value,skip) SELECT id,value,'skipword' FROM " + prefix + "docs");
			statement.execute("INSERT INTO " + prefix + "t(t" + (source.equals("fts5") ? ",rank) VALUES('automerge',8)" : ") VALUES('automerge=8')"));
			statement.execute("CREATE VIEW " + prefix + "v AS SELECT rowid AS id,value FROM t");
			statement.execute("CREATE TRIGGER " + prefix + "feed AFTER INSERT ON docs BEGIN INSERT INTO t(rowid,value,skip) VALUES(new.id,new.value,'skipword'); END");
			statement.execute("CREATE VIRTUAL TABLE " + prefix + "template USING " + destination + "(\"new value\",skip)");
			var target = read(connection,database).getTables().get("template").clone(); target.setName("t"); statement.execute("DROP TABLE " + prefix + "template");
			var desired = read(connection,database); desired.getTables().remove("t"); desired.getTables().add(target);
			desired.getViews().get("v").setStatement(outcome.equals("view") ? "SELECT absent FROM t" : "SELECT rowid AS id,\"new value\" AS value FROM t");
			desired.getTriggers().get("feed").setDefinition("CREATE TRIGGER feed AFTER INSERT ON docs BEGIN INSERT INTO t(rowid,\"new value\",skip) VALUES(new.id,new.value,'skipword'); END");
			if (outcome.equals("copy")) desired.getTables().get("docs").getConstraints().add(new com.sqlapp.data.schemas.CheckConstraint("reject", "id<100"));
			var options = new SqliteTableRebuilder.Options().tables("docs","t").sourceColumns("t",Map.of("new value","value"));
			var before = catalog(connection,database); String xml = desired.asXml();
			assertThrows(IllegalArgumentException.class, () -> new SqliteTableRebuilder().rebuild(connection,desired,options));
			options.convertModule("t"); boolean success = outcome.equals("success");
			if (success) assertEquals(Map.of("docs",3L,"t",3L),new SqliteTableRebuilder().rebuild(connection,desired,options).rowCounts());
			else { assertThrows(SQLException.class, () -> new SqliteTableRebuilder().rebuild(connection,desired,options)); assertEquals(before,catalog(connection,database)); }
			assertEquals(xml,desired.asXml()); assertTrue(connection.getAutoCommit());
			try (var rows = statement.executeQuery("SELECT rowid FROM " + prefix + "t WHERE t MATCH 'alpha'")) { assertTrue(rows.next()); assertEquals(-40,rows.getLong(1)); assertFalse(rows.next()); }
			try (var rows = statement.executeQuery("SELECT rowid FROM " + prefix + "t WHERE t MATCH 'gamma'")) { assertTrue(rows.next()); assertEquals(9007199254740993L,rows.getLong(1)); }
			try (var rows = statement.executeQuery("SELECT id,value FROM " + prefix + "v WHERE id=-40")) { assertTrue(rows.next()); assertEquals("alpha café",rows.getString(2)); }
			boolean fts5 = (success ? destination : source).equals("fts5");
			try (var rows = statement.executeQuery("SELECT " + (fts5 ? "v FROM " + prefix + "t_config WHERE k='automerge'" : "value FROM " + prefix + "t_stat WHERE id=2"))) { assertTrue(rows.next()); assertEquals(8,rows.getInt(1)); }
			statement.execute("INSERT INTO " + prefix + "docs VALUES(101,'next')");
			try (var rows = statement.executeQuery("SELECT rowid FROM " + prefix + "t WHERE t MATCH 'next'")) { assertTrue(rows.next()); assertEquals(101,rows.getInt(1)); assertFalse(rows.next()); }
		}
	}

	static Stream<Arguments> rtreeCases() {
		return Stream.of("main","temp","other\" db").flatMap(database -> Stream.of("rtree","rtree_i32")
				.flatMap(source -> Stream.of("success","precision","view","index").map(outcome -> Arguments.of(database,source,outcome))));
	}

	@ParameterizedTest
	@MethodSource("rtreeCases")
	void convertsRtreeOnlyWhenEveryCopiedValueIsExact(String database,String source,String outcome) throws Exception {
		try(var connection=DriverManager.getConnection("jdbc:sqlite::memory:");var statement=connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS \"other\"\" db\""); String prefix=quote(database)+".";String destination=source.equals("rtree")?"rtree_i32":"rtree";
			statement.execute("CREATE TABLE "+prefix+"audit(id INT)");statement.execute("CREATE INDEX "+prefix+"collision ON audit(id)");
			statement.execute("CREATE TABLE "+prefix+"docs(id INTEGER PRIMARY KEY)"); statement.execute("INSERT INTO "+prefix+"docs VALUES(40)");
			statement.execute("CREATE VIRTUAL TABLE "+prefix+"t USING "+source+"(id,minX,maxX,+payload)");
			String coordinates=outcome.equals("precision")?(source.equals("rtree")?"1.25,9.5":"16777217,16777219"):"1,9";
			statement.execute("INSERT INTO "+prefix+"t VALUES(40,"+coordinates+",x'00FF')");
			statement.execute("CREATE VIEW "+prefix+"v AS SELECT id,payload FROM t");
			statement.execute("CREATE VIRTUAL TABLE "+prefix+"template USING "+destination+"(\"new id\",minX,maxX,+payload)");
			var target=read(connection,database).getTables().get("template").clone(); target.setName("t"); statement.execute("DROP TABLE "+prefix+"template");
			var desired=read(connection,database);desired.getTables().remove("t");desired.getTables().add(target);
			desired.getViews().get("v").setStatement(outcome.equals("view")?"SELECT absent FROM t":"SELECT \"new id\" AS id,payload FROM t");
			if(outcome.equals("index")) { var index=new com.sqlapp.data.schemas.Index("collision");index.getColumns().add(desired.getTables().get("docs").getColumns().get(0));desired.getTables().get("docs").getIndexes().add(index); }
			var options=new SqliteTableRebuilder.Options().tables("t","docs").sourceColumns("t",Map.of("new id","id")).convertModule("t");
			var before=catalog(connection,database);boolean success=outcome.equals("success");
			if(success) {
				var result=new SqliteTableRebuilder().rebuild(connection,desired,options);assertEquals(Map.of("t",1L,"docs",1L),result.rowCounts());
				assertTrue(result.operations().stream().anyMatch(operation->operation.getSqlText().contains(" EXCEPT ")));
			} else { assertThrows(SQLException.class,()->new SqliteTableRebuilder().rebuild(connection,desired,options));assertEquals(before,catalog(connection,database)); }
			try(var rows=statement.executeQuery("SELECT rowid,payload,minX,maxX FROM "+prefix+"t")) {
				assertTrue(rows.next());assertEquals(40,rows.getInt(1));assertArrayEquals(new byte[]{0,(byte)255},rows.getBytes(2));
				if(!outcome.equals("precision")) {assertEquals(1,rows.getDouble(3));assertEquals(9,rows.getDouble(4));}
				else if(source.equals("rtree")) {assertEquals(1.25,rows.getDouble(3));assertEquals(9.5,rows.getDouble(4));}
				else {assertEquals(16777217,rows.getInt(3));assertEquals(16777219,rows.getInt(4));}
			}
			assertTrue(connection.getAutoCommit());
		}
	}

	@Test
	void refusesUnsupportedFamiliesNoOpAndUnknownSelections() throws Exception {
		try(var connection=DriverManager.getConnection("jdbc:sqlite::memory:");var statement=connection.createStatement()) {
			statement.execute("CREATE VIRTUAL TABLE t USING fts5(value)");statement.execute("INSERT INTO t(rowid,value) VALUES(40,'alpha')");
			var desired=read(connection,"main");var before=catalog(connection,"main");
			assertThrows(IllegalArgumentException.class,()->new SqliteTableRebuilder().rebuild(connection,desired,new SqliteTableRebuilder.Options().convertModule("t")));
			assertThrows(IllegalArgumentException.class,()->new SqliteTableRebuilder().rebuild(connection,desired,new SqliteTableRebuilder.Options().convertModule("missing")));
			statement.execute("CREATE VIRTUAL TABLE template USING rtree(id,minX,maxX)");var invalid=read(connection,"main").getTables().get("template").clone();invalid.setName("t");statement.execute("DROP TABLE template");
			desired.getTables().remove("t");desired.getTables().add(invalid);
			assertThrows(IllegalArgumentException.class,()->new SqliteTableRebuilder().rebuild(connection,desired,new SqliteTableRebuilder.Options().convertModule("t")));
			assertEquals(before,catalog(connection,"main"));
		}
	}
}
