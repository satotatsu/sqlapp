/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-sqlite.
 *
 * sqlapp-core-sqlite is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlite is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlite.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sqlite.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.core.test.AbstractTest;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.dialect.util.SqlSplitter;
import com.sqlapp.data.db.dialect.util.SqlSplitter.SplitResult;

public class SqliteSqlSplitterTest extends AbstractTest {
	@Test
	void utf8BomAndLegacyDialectUseTheSameTriggerBoundaries() throws Exception {
		String sql = "\ufeffcreate temp trigger tr after insert on t begin select 1; select 2; end;INSERT INTO t VALUES(1);";
		for (var target : List.of(dialect,
				com.sqlapp.data.db.dialect.DialectResolver.getInstance().getDialect("SQLite", 3, 7, 0))) {
			var splits = target.createSqlSplitter().parse(sql);
			assertEquals(2, splits.size());
			try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:");
					var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE t(id INT)");
				for (var split : splits)
					statement.execute(split.getText());
			}
		}
	}

	@Test
	void metadataXmlAndGeneratedScriptRecreateAnExecutableTrigger() throws Exception {
		String script;
		try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(id INT PRIMARY KEY)");
			statement.execute("CREATE TABLE audit(value TEXT)");
			statement.execute("CREATE VIEW v AS SELECT value FROM audit");
			statement.execute("create trigger \"tr;END\" after insert on t begin "
					+ "insert into audit values(CASE WHEN NEW.id=1 THEN 'one;END' ELSE 'other' END);"
					+ "insert into audit values('last');end");
			var reader = dialect.getCatalogReader().getSchemaReader();
			var original = reader.getAllFull(connection).stream().filter(s -> s.getName().equals("main")).findFirst()
					.orElseThrow();
			com.sqlapp.data.schemas.Schema schema = com.sqlapp.data.schemas.SchemaUtils
					.readXml(new java.io.StringReader(original.asXml()));
			var registry = dialect.createSqlFactoryRegistry();
			var generated = new StringBuilder("/* generated schema */");
			for (var table : schema.getTables())
				for (var operation : registry.createSql(table, com.sqlapp.data.db.sql.SqlType.CREATE))
					generated.append(operation.getSqlText()).append(';');
			for (var view : schema.getViews())
				for (var operation : registry.createSql(view, com.sqlapp.data.db.sql.SqlType.CREATE))
					generated.append(operation.getSqlText()).append(';');
			for (var trigger : schema.getTriggers())
				for (var operation : registry.createSql(trigger, com.sqlapp.data.db.sql.SqlType.CREATE))
					generated.append(operation.getSqlText()).append(';');
			script = generated.append("INSERT INTO t VALUES(1);").toString();
		}
		try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			for (var split : dialect.createSqlSplitter().parse(script))
				if (!split.getTextType().isComment())
					statement.execute(split.getText());
			try (var result = statement.executeQuery("SELECT group_concat(value,'|') FROM v")) {
				assertTrue(result.next());
				assertEquals("one;END|last", result.getString(1));
			}
		}
	}

	@Test
	void explainTriggerAndConsecutiveTriggersStayComplete() throws Exception {
		String body = " AFTER INSERT ON t BEGIN SELECT CASE WHEN NEW.id=1 THEN 'END;' ELSE 'x' END; END";
		String sql = "CREATE TABLE t(id INT);EXPLAIN CREATE TRIGGER plan_only" + body + ";"
				+ "CREATE TRIGGER first_trigger" + body + ";CREATE TRIGGER second_trigger" + body + ";";
		var splits = dialect.createSqlSplitter().parse(sql);
		assertEquals(4, splits.size());
		try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			for (var split : splits)
				statement.execute(split.getText());
			try (var result = statement.executeQuery("SELECT count(*) FROM sqlite_master WHERE type='trigger'")) {
				assertTrue(result.next());
				assertEquals(2, result.getInt(1));
			}
		}
	}

	@Test
	void inlineLowercaseTriggersKeepNestedCasesAndAdjacentStatements() throws Exception {
		String sql = "CREATE TABLE t(id INT);CREATE TABLE audit(value TEXT);"
				+ "create /* header */ trigger tr after insert on t begin "
				+ "insert into audit values(case when NEW.id>0 then case when NEW.id=1 then 'one; END' else 'many' end else 'zero' end);"
				+ "insert into audit values('second');end;INSERT INTO t VALUES(1);";
		var splits = dialect.createSqlSplitter().parse(sql);
		assertEquals(4, splits.size());
		assertTrue(splits.get(2).getText().endsWith("end"));
		try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			for (var split : splits)
				statement.execute(split.getText());
			try (var result = statement.executeQuery("SELECT group_concat(value,'|') FROM audit")) {
				assertTrue(result.next());
				assertEquals("one; END|second", result.getString(1));
			}
		}
	}

	@Test
	void commentsAndAllIdentifierQuotesRemainOpaque() throws Exception {
		String sql = "-- standalone END;\nCREATE TABLE [t;END]([value;] TEXT);"
				+ "CREATE TEMPORARY TRIGGER `tr;END` AFTER INSERT ON [t;END] BEGIN "
				+ "SELECT '/* unclosed is text', '-- not a comment', 'it''s; END'; /* END; */ "
				+ "SELECT 2; -- END;\nEND /* final END */;"
				+ "INSERT INTO [t;END] VALUES('kept');SELECT \"value;\" FROM [t;END]";
		var splits = dialect.createSqlSplitter().parse(sql);
		assertEquals(5, splits.size());
		assertTrue(splits.get(0).getTextType().isComment());
		try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			for (var split : splits)
				if (!split.getTextType().isComment())
					statement.execute(split.getText());
			try (var result = statement.executeQuery("SELECT \"value;\" FROM [t;END]")) {
				assertTrue(result.next());
				assertEquals("kept", result.getString(1));
			}
		}
	}

	@Test
	void noFinalDelimiterAndTransactionEndAreSupported() throws Exception {
		String sql = "CREATE TABLE t(id INT);BEGIN;INSERT INTO t VALUES(1);END;"
				+ "CREATE TRIGGER tr AFTER INSERT ON t BEGIN SELECT 1; END";
		var splits = dialect.createSqlSplitter().parse(sql);
		assertEquals(5, splits.size());
		try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			for (var split : splits)
				statement.execute(split.getText());
		}
	}

	@Test
	void incompleteInputFailsBeforeItCanBeExecutedAsFragments() {
		for (String sql : List.of("CREATE TRIGGER tr AFTER INSERT ON t BEGIN SELECT 1;",
				"CREATE TRIGGER tr AFTER INSERT ON t BEGIN SELECT 1; END SELECT 2;", "SELECT 'unfinished",
				"SELECT \"unfinished", "SELECT [unfinished", "SELECT `unfinished", "SELECT 1 /* unfinished")) {
			assertThrows(com.sqlapp.exceptions.InvalidTextException.class, () -> dialect.createSqlSplitter().parse(sql),
					sql);
		}
	}

	Dialect dialect = DialectHolder.defaultDialect;

	@Test
	public void test1() {
		String text = this.getResource("test.sql");
		SqlSplitter sqlSplitter = dialect.createSqlSplitter();
		List<SplitResult> splits = sqlSplitter.parse(text);
		assertEquals(8, splits.size());
		int i = 0;
		assertEquals("/*create table comment*/", splits.get(i++).getText());
	}

}
