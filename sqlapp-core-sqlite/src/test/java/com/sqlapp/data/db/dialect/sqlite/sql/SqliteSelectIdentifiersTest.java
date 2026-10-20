/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class SqliteSelectIdentifiersTest {
	private Table read(Connection connection, String schema, String name) {
		var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setSchemaName(schema);
		reader.setObjectName(name);
		return reader.getAllFull(connection).get(0);
	}

	@Test
	void typedCompositeKeyLookupTargetsTheAttachedTable() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS other");
			statement.execute("CREATE TABLE other.t(a INT,b TEXT,value INT,PRIMARY KEY(a,b))");
			statement.execute("INSERT INTO other.t VALUES(1,'one',7),(1,'two',8)");
			statement.execute("CREATE TEMP TABLE t(a INT,b TEXT,value INT)");
			statement.execute("INSERT INTO temp.t VALUES(1,'one',999)");
			var table = read(connection, "other", "t");
			var row = table.getRows().newElement();
			row.put("a", 1);
			row.put("b", "one");
			table.getRows().add(row);
			for (var dialect : List.of(DialectHolder.defaultDialect,
					DialectResolver.getInstance().getDialect("SQLite", 3, 7, 0))) {
				var node = dialect.createSqlFactoryRegistry().createSqlNodes(table, SqlType.SELECT).get(0);
				try (var parameters = node.eval(row); var query = parameters.createStatement(connection)) {
					parameters.setBind(query);
					try (var result = query.executeQuery()) {
						assertTrue(result.next());
						assertEquals(7, result.getInt("value"));
						assertFalse(result.next());
					}
				}
			}
		}
	}

	@Test
	void identifiersWithBracketsQuotesAndMixedCaseAreExecutable() throws Exception {
		var dialect = DialectHolder.defaultDialect;
		var registry = dialect.createSqlFactoryRegistry();
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			for (var name : List.of("order", "MiXeD_Name", "a]b", "a]\"b", "a\"b", "a`b", "\"Quoted\"", "`Quoted`")) {
				var table = new Table(name).setSchemaName("main");
				table.getColumns().add(new Column("value").setDataType(DataType.INT));
				for (var operation : registry.createSql(table, SqlType.CREATE))
					statement.execute(operation.getSqlText());
				statement.execute("INSERT INTO " + dialect.getObjectFullName("main", name) + " VALUES(7)");
				try (var result = statement
						.executeQuery(registry.createSql(table, SqlType.SELECT_TABLE).get(0).getSqlText())) {
					assertTrue(result.next());
					assertEquals(7, result.getInt(1));
				}
				try (var query = connection.prepareStatement("SELECT name FROM main.sqlite_master WHERE name=?")) {
					query.setString(1, name);
					try (var result = query.executeQuery()) {
						assertTrue(result.next());
						assertEquals(dialect.nativeCaseString(name), result.getString(1));
					}
				}
				assertEquals(name, dialect.unQuote(dialect.quote(name)));
				for (var operation : registry.createSql(table, SqlType.DROP))
					statement.execute(operation.getSqlText());
			}
		}
	}

	@Test
	void specialAttachedAndColumnNamesSurviveMetadataXmlAndKeySql() throws Exception {
		var dialect = DialectHolder.defaultDialect;
		var registry = dialect.createSqlFactoryRegistry();
		String schema = "Mixed]Db", name = "Order]Name\"", key = "Key]Name", value = "Payload]Name\"";
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS \"Mixed]Db\"");
			var source = new Table(name).setSchemaName(schema);
			source.getColumns().add(new Column(key).setDataType(DataType.INT));
			source.getColumns().add(new Column(value).setDataType(DataType.VARCHAR));
			source.setPrimaryKey((String) null, source.getColumns().get(key));
			for (var operation : registry.createSql(source, SqlType.CREATE))
				statement.execute(operation.getSqlText());
			var table = read(connection, schema, name);
			Table restored = com.sqlapp.data.schemas.SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			for (var operation : registry.createSql(table, SqlType.DROP))
				statement.execute(operation.getSqlText());
			for (var operation : registry.createSql(restored, SqlType.CREATE))
				statement.execute(operation.getSqlText());
			String qualified = dialect.getObjectFullName(schema, name);
			try (var insert = connection.prepareStatement("INSERT INTO " + qualified + " VALUES(?,?)")) {
				insert.setInt(1, 3);
				insert.setString(2, "kept");
				insert.executeUpdate();
			}
			// Native JDBC placeholders avoid treating arbitrary SQL identifiers as
			// expression-language variables.
			registry.getTableOptions().setParameterExpression((column, defaultValue) -> "?");
			try (var query = connection
					.prepareStatement(registry.createSql(restored, SqlType.SELECT).get(0).getSqlText())) {
				query.setInt(1, 3);
				try (var result = query.executeQuery()) {
					assertTrue(result.next());
					assertEquals("kept", result.getString(value));
					assertFalse(result.next());
				}
			}
		}
	}

	@Test
	void bracketAndGeneratedFallbackQuotesUnwrapAndDoNotGetQuotedTwice() throws Exception {
		var dialect = DialectHolder.defaultDialect;
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			for (var quoted : List.of("[order]", "\"a]\"\"b\"")) {
				assertTrue(dialect.isQuoted(quoted));
				assertEquals(quoted, dialect.quote(quoted));
				try (var result = statement.executeQuery("SELECT 1 AS " + quoted)) {
					assertEquals(dialect.unQuote(quoted), result.getMetaData().getColumnName(1));
				}
			}
			assertNull(dialect.unQuote(null));
			assertEquals("", dialect.unQuote(""));
			assertFalse(dialect.isQuoted("["));
		}
	}

	@Test
	void lockRequestsFailInsteadOfReturningNoSql() {
		for (var dialect : List.of(DialectHolder.defaultDialect,
				DialectResolver.getInstance().getDialect("SQLite", 3, 7, 0))) {
			var error = assertThrows(IllegalArgumentException.class,
					() -> dialect.createSqlFactoryRegistry().createSql(new Table("t"), SqlType.LOCK));
			assertTrue(error.getMessage().contains("transaction"));
		}
	}
}
