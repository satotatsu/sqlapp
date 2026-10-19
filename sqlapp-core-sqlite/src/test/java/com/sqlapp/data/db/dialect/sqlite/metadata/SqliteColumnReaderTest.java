/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlite.
 */
package com.sqlapp.data.db.dialect.sqlite.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SqliteColumnReaderTest {
	@Test
	void formulaParsingIgnoresQuotedNamesCommentsAndNestedCastAs() {
		assertEquals("printf('%s AS(', [source)value]) || ')'", SqliteColumnReader.extractFormula(
				"\"generated AS(fake)\" TEXT /* AS(fake) */ GENERATED ALWAYS AS /* ) */ (printf('%s AS(', [source)value]) || ')') STORED"));
		assertEquals(null, SqliteColumnReader.extractFormula("value TEXT DEFAULT (CAST('1' AS TEXT))"));
		assertEquals(null, SqliteColumnReader.extractFormula("value TEXT DEFAULT 'AS(fake)' /* AS(fake) */"));
	}

	@Test
	void generatedExpressionsWithQuotedIdentifiersAndLeadingCommentsSurviveXml() throws Exception {
		try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute(
					"""
							CREATE TABLE t([source)value] TEXT,
							 /* AS(fake) */ "generated AS(fake)" TEXT AS /* ) */ (printf('%s AS(', [source)value]) || ')') STORED,
							 /* leading column comment */ 'single generated' TEXT AS (upper([source)value])))
							""");
			var dialect = com.sqlapp.data.db.dialect.sqlite.DialectHolder.defaultDialect;
			var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setSchemaName("main");
			reader.setObjectName("t");
			var table = reader.getAllFull(connection).get(0);
			assertEquals("printf('%s AS(', [source)value]) || ')'",
					table.getColumns().get("generated AS(fake)").getFormula());
			assertEquals("upper([source)value])", table.getColumns().get("single generated").getFormula());
			com.sqlapp.data.schemas.Table restored = com.sqlapp.data.schemas.SchemaUtils
					.readXml(new java.io.StringReader(table.asXml()));
			statement.execute("DROP TABLE t");
			for (var operation : dialect.createSqlFactoryRegistry().createSql(restored,
					com.sqlapp.data.db.sql.SqlType.CREATE))
				statement.execute(operation.getSqlText());
			statement.execute("INSERT INTO t([source)value]) VALUES ('hello')");
			try (var rs = statement.executeQuery("SELECT \"generated AS(fake)\", \"single generated\" FROM t")) {
				org.junit.jupiter.api.Assertions.assertTrue(rs.next());
				assertEquals("hello AS()", rs.getString(1));
				assertEquals("HELLO", rs.getString(2));
			}
		}
	}

	@Test
	void unregisteredDeclarationsUseAffinityWithoutLosingTheirDdl() throws Exception {
		var dialect = com.sqlapp.data.db.dialect.sqlite.DialectHolder.defaultDialect;
		try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE affinity(v VARCHAR(30), n custom_money, i FLOATING POINT, bytes)");
			var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setSchemaName("main");
			reader.setObjectName("affinity");
			var table = reader.getAllFull(connection).get(0);
			assertEquals(com.sqlapp.data.db.datatype.DataType.VARCHAR, table.getColumns().get("v").getDataType());
			assertEquals(com.sqlapp.data.db.datatype.DataType.NUMERIC, table.getColumns().get("n").getDataType());
			assertEquals(com.sqlapp.data.db.datatype.DataType.BIGINT, table.getColumns().get("i").getDataType());
			assertEquals(com.sqlapp.data.db.datatype.DataType.BLOB, table.getColumns().get("bytes").getDataType());
			assertEquals("custom_money", table.getColumns().get("n").getSpecifics().get("sqlite.declaredType"));
			com.sqlapp.data.schemas.Table restored = com.sqlapp.data.schemas.SchemaUtils
					.readXml(new java.io.StringReader(table.asXml()));
			statement.execute("DROP TABLE affinity");
			for (var operation : dialect.createSqlFactoryRegistry().createSql(restored,
					com.sqlapp.data.db.sql.SqlType.CREATE))
				statement.execute(operation.getSqlText());
			try (var result = statement.executeQuery("PRAGMA table_info(affinity)")) {
				result.next();
				assertEquals("VARCHAR(30)", result.getString("type"));
				result.next();
				assertEquals("CUSTOM_MONEY", result.getString("type").toUpperCase(java.util.Locale.ROOT));
				result.next();
				assertEquals("FLOATING POINT", result.getString("type"));
			}
		}
	}

	@Test
	void ignoresParenthesesInQuotedObjectNames() {
		assertEquals(java.util.List.of("lower(value) DESC", "id"), SqliteColumnReader.splitColumnDefinitions(
				"CREATE INDEX \"ix(quoted\" ON \"table(quoted\" (lower(value) DESC, id) WHERE id>0"));
	}

	@Test
	void splitsDefinitionsWithoutSplittingExpressionsOrStrings() {
		var definitions = SqliteColumnReader.splitColumnDefinitions("""
				CREATE TABLE sample (
				 id INTEGER PRIMARY KEY,
				 "display name" TEXT AS (printf('%s,%s', first_name, last_name)) VIRTUAL,
				 total NUMERIC GENERATED ALWAYS AS ((price + tax) * quantity) STORED
				) STRICT
				""");

		assertEquals(3, definitions.size());
		assertEquals("printf('%s,%s', first_name, last_name)", SqliteColumnReader.extractFormula(definitions.get(1)));
		assertEquals("(price + tax) * quantity", SqliteColumnReader.extractFormula(definitions.get(2)));
	}

	@Test
	void stripsOnlyTheIndexKeyOrder() {
		assertEquals("lower(code)", SqliteIndexReader.stripOrder("lower(code) DESC"));
		assertEquals("CASE WHEN score > 0 THEN 'DESC' END",
				SqliteIndexReader.stripOrder("CASE WHEN score > 0 THEN 'DESC' END ASC"));
	}
}
