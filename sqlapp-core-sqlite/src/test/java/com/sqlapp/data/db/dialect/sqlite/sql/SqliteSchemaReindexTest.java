/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;
import static com.sqlapp.data.db.dialect.sqlite.sql.SqliteBatchRebuildTest.*;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sqlite.Collation;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

class SqliteSchemaReindexTest {
	@ParameterizedTest
	@CsvSource({"main,false","temp,false","other\" db,false","main,true","temp,true","other\" db,true"})
	void rebuildsOnlyModeledTablesAndLetsTheCallerRollbackPartialFailure(String database, boolean failure) throws Exception {
		try(var connection=DriverManager.getConnection("jdbc:sqlite::memory:");var statement=connection.createStatement()) {
			var reverse=new AtomicBoolean();var fold=new AtomicBoolean();
			Collation.create(connection,"reversed",new Collation() {
				@Override protected int xCompare(String left,String right) {return reverse.get()?right.compareTo(left):left.compareTo(right);}
			});
			Collation.create(connection,"folded",new Collation() {
				@Override protected int xCompare(String left,String right) {return fold.get()?left.compareToIgnoreCase(right):left.compareTo(right);}
			});
			statement.execute("ATTACH ':memory:' AS \"other\"\" db\"");
			for(String name:new String[]{"main","temp","other\" db"}) {
				String prefix=quote(name)+".";
				statement.execute("CREATE TABLE "+prefix+"a(value TEXT COLLATE reversed)");
				statement.execute("CREATE INDEX "+prefix+"a_idx ON a(value)");statement.execute("INSERT INTO "+prefix+"a VALUES('a'),('b')");
				statement.execute("CREATE TABLE "+prefix+"b(value TEXT COLLATE folded UNIQUE)");statement.execute("INSERT INTO "+prefix+"b VALUES('A'),('a')");
				statement.execute("CREATE TABLE "+prefix+"unselected(value TEXT COLLATE reversed)");
				statement.execute("CREATE INDEX "+prefix+"unselected_idx ON unselected(value)");statement.execute("INSERT INTO "+prefix+"unselected VALUES('a'),('b')");
				statement.execute("CREATE TABLE "+prefix+"empty(value INT)");
			}
			String prefix=quote(database)+".";
			statement.execute("CREATE VIRTUAL TABLE "+prefix+"search USING fts5(body)");statement.execute("INSERT INTO "+prefix+"search VALUES('alpha')");
			statement.execute("CREATE VIRTUAL TABLE "+prefix+"coordinates USING rtree(id,x1,x2)");statement.execute("INSERT INTO "+prefix+"coordinates VALUES(1,0,1)");
			var actual=read(connection,database);var schema=new Schema(database);
			for(String name:new String[]{"a","b","empty","search","coordinates"}) schema.getTables().add(actual.getTables().get(name).clone());
			String xml=schema.asXml();
			var operations=DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(schema,SqlType.REBUILD);
			assertEquals(3,operations.size());assertTrue(operations.stream().allMatch(operation->operation.getSqlType()==SqlType.REBUILD));
			reverse.set(true);fold.set(failure);connection.setAutoCommit(false);
			if(failure) {
				statement.execute(operations.get(0).getSqlText());
				assertThrows(SQLException.class,()->statement.execute(operations.get(1).getSqlText()));
				assertFalse(connection.getAutoCommit());connection.rollback();
			} else {
				for(var operation:operations) statement.execute(operation.getSqlText());
				assertFalse(connection.getAutoCommit());connection.commit();
			}
			for(String name:new String[]{"main","temp","other\" db"}) {
				try(var rows=statement.executeQuery("SELECT value FROM "+quote(name)+".a INDEXED BY a_idx ORDER BY value")) {
					assertTrue(rows.next());assertEquals(name.equals(database)&&!failure?"b":"a",rows.getString(1));
				}
			}
			try(var rows=statement.executeQuery("SELECT value FROM "+prefix+"unselected INDEXED BY unselected_idx ORDER BY value")) {assertTrue(rows.next());assertEquals("a",rows.getString(1));}
			try(var rows=statement.executeQuery("SELECT count(*) FROM "+prefix+"search WHERE search MATCH 'alpha'")) {assertTrue(rows.next());assertEquals(1,rows.getInt(1));}
			try(var rows=statement.executeQuery("SELECT count(*) FROM "+prefix+"coordinates WHERE x1<=0 AND x2>=1")) {assertTrue(rows.next());assertEquals(1,rows.getInt(1));}
			assertEquals(xml,schema.asXml());
		}
	}

	@Test
	void validatesSchemaMembershipNamesAndLegacyGeneration() throws Exception {
		var registry=DialectHolder.defaultDialect.createSqlFactoryRegistry();
		assertTrue(registry.createSql(new Schema("main"),SqlType.REBUILD).isEmpty());
		for(String name:new String[]{""," ","bad\0name"}) assertThrows(IllegalArgumentException.class,()->registry.createSql(new Schema(name),SqlType.REBUILD));
		var schema=new Schema("main");schema.getTables().add(new Table("t").setSchemaName("other"));
		// Canonical Schema ownership takes precedence over a detached reference's old schema.
		assertEquals(registry.createSql(new Table("t").setSchemaName("main"),SqlType.REBUILD).get(0).getSqlText(),registry.createSql(schema,SqlType.REBUILD).get(0).getSqlText());
		schema.getTables().clear();schema.getTables().add(new Table("quoted\" table"));String xml=schema.asXml();
		var legacy=DialectResolver.getInstance().getDialect("SQLite",3,7,0).createSqlFactoryRegistry();
		assertEquals(registry.createSql(schema,SqlType.REBUILD).get(0).getSqlText(),legacy.createSql(schema,SqlType.REBUILD).get(0).getSqlText());
		assertEquals(xml,schema.asXml());
	}
}
