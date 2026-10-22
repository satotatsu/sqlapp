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

class SqliteLegacyFtsRebuildTest {
	@Test
	void preservesCompressionTokenizerAndQuotedColumnsWithoutRuntimeSettings() throws Exception {
		try (var connection=DriverManager.getConnection("jdbc:sqlite::memory:");var statement=connection.createStatement()) {
			org.sqlite.Function.create(connection,"reverse_text",new org.sqlite.Function() {
				@Override protected void xFunc() throws SQLException {
					String value=value_text(0); if(value==null) result(); else result(new StringBuilder(value).reverse().toString());
				}
			});
			statement.execute("CREATE VIRTUAL TABLE t USING fts4(\"doc, text\",label,spare,notindexed=label,notindexed=spare,tokenize=unicode61 \"remove_diacritics=2\",compress=reverse_text,uncompress=reverse_text)");
			statement.execute("INSERT INTO t(docid,\"doc, text\",label,spare) VALUES(-40,'Café alpha','hidden',NULL),(9007199254740993,'beta','hidden',NULL)");
			new SqliteTableRebuilder().rebuild(connection,read(connection,"main"));
			try(var rows=statement.executeQuery("SELECT docid,\"doc, text\",spare FROM t WHERE t MATCH 'cafe'")) {
				assertTrue(rows.next());assertEquals(-40,rows.getLong(1));assertEquals("Café alpha",rows.getString(2));assertNull(rows.getObject(3));assertFalse(rows.next());
			}
			try(var rows=statement.executeQuery("SELECT docid FROM t WHERE t MATCH 'beta'")) { assertTrue(rows.next());assertEquals(9007199254740993L,rows.getLong(1)); }
			try(var rows=statement.executeQuery("SELECT count(*) FROM t WHERE t MATCH 'hidden'")) { assertTrue(rows.next());assertEquals(0,rows.getInt(1)); }
			statement.execute("CREATE VIRTUAL TABLE plain USING fts3(value)");statement.execute("INSERT INTO plain(docid,value) VALUES(20,'plain')");
			var options=new SqliteTableRebuilder.Options().tables("plain");
			new SqliteTableRebuilder().rebuild(connection,read(connection,"main"),options);
			try(var rows=statement.executeQuery("SELECT docid FROM plain WHERE plain MATCH 'plain'")) { assertTrue(rows.next());assertEquals(20,rows.getInt(1)); }
		}
	}

	@Test
	void refusesToDropLanguageIdentityAndValidatesScalarOptionParsing() throws Exception {
		String ddl="CREATE VIRTUAL TABLE t USING fts4(value,/* opaque = , */notindexed=value,notindexed=other,languageid='l''id',tokenize=unicode61 \"remove_diacritics=2\")";
		assertEquals("l'id",com.sqlapp.data.db.dialect.sqlite.util.SqliteDdl.virtualTableOption(ddl,"languageid"));
		assertNull(com.sqlapp.data.db.dialect.sqlite.util.SqliteDdl.virtualTableOption(ddl,"content"));
		assertThrows(IllegalArgumentException.class,()->com.sqlapp.data.db.dialect.sqlite.util.SqliteDdl.virtualTableOption("CREATE VIRTUAL TABLE t USING fts4(value,content='a',content='b')","content"));
		try(var connection=DriverManager.getConnection("jdbc:sqlite::memory:");var statement=connection.createStatement()) {
			statement.execute("CREATE VIRTUAL TABLE t USING fts4(value,languageid='lid')");statement.execute("INSERT INTO t(docid,value,lid) VALUES(40,'alpha',5)");
			statement.execute("CREATE VIRTUAL TABLE template USING fts4(value)");
			var target=read(connection,"main").getTables().get("template").clone();target.setName("t");statement.execute("DROP TABLE template");
			var desired=read(connection,"main");desired.getTables().remove("t");desired.getTables().add(target);var before=catalog(connection,"main");
			assertThrows(IllegalArgumentException.class,()->new SqliteTableRebuilder().rebuild(connection,desired));assertEquals(before,catalog(connection,"main"));
			try(var rows=statement.executeQuery("SELECT docid FROM t WHERE t MATCH 'alpha' AND lid=5")) {assertTrue(rows.next());assertEquals(40,rows.getInt(1));}
		}
	}
	static Stream<Arguments> cases() {
		return Stream.of("main","temp","other\" db").flatMap(database -> Stream.of("fts3","fts4","language","external","externalLanguage")
				.flatMap(kind -> Stream.of("success","view","copy").map(outcome -> Arguments.of(database,kind,outcome))));
	}

	@ParameterizedTest
	@MethodSource("cases")
	void preservesTextDocidsLanguageAndConfigurationOrRestoresEverything(String database,String kind,String outcome) throws Exception {
		try (var connection=DriverManager.getConnection("jdbc:sqlite::memory:"); var statement=connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS \"other\"\" db\""); String prefix=quote(database)+".";
			boolean fts3=kind.equals("fts3"), language=kind.toLowerCase().contains("language"), external=kind.startsWith("external");
			statement.execute("CREATE TABLE "+prefix+"docs(id INTEGER PRIMARY KEY,value TEXT,skip TEXT,other TEXT,lid INT)");
			statement.execute("INSERT INTO "+prefix+"docs VALUES(40,'running alpha','hidden','hidden',0),(80,'running beta','hidden','hidden',5)");
			String module=fts3?"fts3":"fts4";
			String options=",tokenize=porter"+(fts3?"":",prefix='2,3',notindexed=skip,notindexed=other")
					+(external?",content='docs'":"")+(language?",languageid='lid'":"");
			statement.execute("CREATE VIRTUAL TABLE "+prefix+"t USING "+module+"(value,skip,other"+options+")");
			statement.execute("INSERT INTO "+prefix+"t(docid,value,skip,other"+(language?",lid":"")+") SELECT id,value,skip,other"+(language?",lid":"")+" FROM "+prefix+"docs");
			statement.execute("INSERT INTO "+prefix+"t(t) VALUES('automerge=4')");
			statement.execute("CREATE VIEW "+prefix+"v AS SELECT docid AS id,value FROM t");
			String newOptions=options;
			if (language && !external) newOptions=options.replace("languageid='lid'","languageid='new lang'");
			statement.execute("CREATE VIRTUAL TABLE "+prefix+"template USING "+module+"(\"new value\",skip,other"+newOptions+")");
			var target=read(connection,database).getTables().get("template").clone(); target.setName("t"); statement.execute("DROP TABLE "+prefix+"template");
			var desired=read(connection,database); desired.getTables().remove("t"); desired.getTables().add(target);
			var rebuildOptions=new SqliteTableRebuilder.Options().tables("docs","t").sourceColumns("t",Map.of("new value","value"));
			if (external) {
				desired.getTables().get("docs").getColumns().get("value").setName("new value");
				rebuildOptions.sourceColumns("docs",Map.of("new value","value"));
			}
			if (outcome.equals("copy")) desired.getTables().get("docs").getConstraints().add(new com.sqlapp.data.schemas.CheckConstraint("reject","id<50"));
			desired.getViews().get("v").setStatement(outcome.equals("view")?"SELECT absent FROM t":"SELECT docid AS id,\"new value\" AS value FROM t");
			var before=catalog(connection,database); String xml=desired.asXml(); boolean success=outcome.equals("success");
			if (success) assertEquals(Map.of("docs",2L,"t",2L),new SqliteTableRebuilder().rebuild(connection,desired,rebuildOptions).rowCounts());
			else { assertThrows(SQLException.class,()->new SqliteTableRebuilder().rebuild(connection,desired,rebuildOptions)); assertEquals(before,catalog(connection,database)); }
			assertEquals(xml,desired.asXml()); assertTrue(connection.getAutoCommit());
			String lid=language && !external && success?"\"new lang\"":"lid";
			try (var rows=statement.executeQuery("SELECT docid FROM "+prefix+"t WHERE t MATCH 'run'"+(language?" AND "+lid+"=5":"")+" ORDER BY docid")) {
				assertTrue(rows.next()); assertEquals(language?80:40,rows.getInt(1)); if (!language) { assertTrue(rows.next()); assertEquals(80,rows.getInt(1)); } assertFalse(rows.next());
			}
			try (var rows=statement.executeQuery("SELECT id,value FROM "+prefix+"v ORDER BY id")) {
				assertTrue(rows.next()); assertEquals(40,rows.getInt(1)); assertEquals("running alpha",rows.getString(2)); assertTrue(rows.next()); assertEquals(80,rows.getInt(1)); assertFalse(rows.next());
			}
			if (!fts3) try (var rows=statement.executeQuery("SELECT count(*) FROM "+prefix+"t WHERE t MATCH 'hidden'")) { assertTrue(rows.next()); assertEquals(0,rows.getInt(1)); }
			try (var rows=statement.executeQuery("SELECT value FROM "+prefix+"t_stat WHERE id=2")) { assertTrue(rows.next()); assertEquals(4,rows.getInt(1)); }
			statement.execute("INSERT INTO "+prefix+"t(t) VALUES('integrity-check')");
		}
	}

	@Test
	void rejectsContentlessAndStaleExternalContent() throws Exception {
		try(var connection=DriverManager.getConnection("jdbc:sqlite::memory:");var statement=connection.createStatement()) {
			statement.execute("CREATE VIRTUAL TABLE t USING fts4(value,content='')"); statement.execute("INSERT INTO t(docid,value) VALUES(40,'alpha')");
			var reader=com.sqlapp.data.db.dialect.sqlite.DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader(); reader.setSchemaName("main"); reader.setObjectName("t");
			var target=reader.getAllFull(connection).get(0);
			assertThrows(IllegalArgumentException.class,()->new SqliteTableRebuilder().rebuild(connection,target));
			try(var rows=statement.executeQuery("SELECT docid FROM t WHERE t MATCH 'alpha'")) { assertTrue(rows.next());assertEquals(40,rows.getInt(1)); }
			statement.execute("DROP TABLE t"); statement.execute("CREATE TABLE docs(id INTEGER PRIMARY KEY,value TEXT)");statement.execute("INSERT INTO docs VALUES(40,'alpha')");
			statement.execute("CREATE VIRTUAL TABLE t USING fts4(value,content='docs')");statement.execute("INSERT INTO t(t) VALUES('rebuild')"); statement.execute("UPDATE docs SET value='beta'");
			var desired=read(connection,"main");var before=catalog(connection,"main");
			assertThrows(SQLException.class,()->new SqliteTableRebuilder().rebuild(connection,desired));assertEquals(before,catalog(connection,"main")); assertTrue(connection.getAutoCommit());
		}
	}
}
