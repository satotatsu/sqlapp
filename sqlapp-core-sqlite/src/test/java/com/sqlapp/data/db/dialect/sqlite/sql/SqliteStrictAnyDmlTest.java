/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;

class SqliteStrictAnyDmlTest {
	static Stream<Arguments> values() {
		return Stream.of(Arguments.of("000123", "text"), Arguments.of("hello", "text"),
				Arguments.of(9007199254740993L, "integer"), Arguments.of(1.25d, "real"),
				Arguments.of(new byte[] { 0, 1, (byte) 255 }, "blob"), Arguments.of(null, "null"));
	}

	@ParameterizedTest
	@MethodSource("values")
	void strictAnyPreservesValueAndStorageClassThroughXmlAndGeneratedInsert(Object value, String storage)
			throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS other");
			statement.execute("CREATE TABLE other.t(id ANY PRIMARY KEY, payload ANY) STRICT");
			Table table = SchemaUtils.readXml(new java.io.StringReader(read(connection, "other").asXml()));
			assertEquals(DataType.ANY_DATA, table.getColumns().get("payload").getDataType());
			assertEquals("ANY", table.getColumns().get("payload").getSpecifics().get("sqlite.declaredType"));
			statement.execute("DROP TABLE other.t");
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			for (var operation : registry.createSql(table, SqlType.CREATE))
				statement.execute(operation.getSqlText());
			var row = table.getRows().newElement();
			row.put("id", "0001");
			row.put("payload", value);
			try (var parameters = registry.createSqlNodes(table, SqlType.INSERT).get(0).eval(row);
					var insert = parameters.createStatement(connection)) {
				parameters.setBind(insert);
				assertEquals(1, insert.executeUpdate());
			}
			try (var rows = statement.executeQuery("SELECT id,typeof(id),payload,typeof(payload) FROM other.t")) {
				assertTrue(rows.next());
				assertEquals("0001", rows.getString(1));
				assertEquals("text", rows.getString(2));
				assertEquals(storage, rows.getString(4));
				if (value instanceof byte[] bytes)
					assertArrayEquals(bytes, rows.getBytes(3));
				else if (value instanceof Number number)
					assertEquals(number.toString(), rows.getObject(3).toString());
				else
					assertEquals(value, rows.getObject(3));
				assertFalse(rows.next());
			}
		}
	}

	@Test
	void generatedUpdateSelectAndDeleteDistinguishTextAndIntegerAnyKeys() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(id ANY PRIMARY KEY, payload ANY) STRICT");
			statement.execute("INSERT INTO t VALUES('0001','text key'),(1,'integer key')");
			var table = read(connection, "main");
			var row = table.getRows().newElement();
			row.put("id", "0001");
			row.put("payload", new byte[] { 1, 2 });
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			for (SqlType type : java.util.List.of(SqlType.UPDATE, SqlType.SELECT, SqlType.DELETE)) {
				try (var parameters = registry.createSqlNodes(table, type).get(0).eval(row);
						var query = parameters.createStatement(connection)) {
					parameters.setBind(query);
					if (type == SqlType.SELECT) {
						try (var rows = query.executeQuery()) {
							assertTrue(rows.next());
							assertEquals("0001", rows.getString("id"));
							assertArrayEquals(new byte[] { 1, 2 }, rows.getBytes("payload"));
							assertFalse(rows.next());
						}
					} else
						assertEquals(1, query.executeUpdate());
				}
			}
			try (var rows = statement.executeQuery("SELECT id,typeof(id),payload FROM t")) {
				assertTrue(rows.next());
				assertEquals(1, rows.getInt(1));
				assertEquals("integer", rows.getString(2));
				assertEquals("integer key", rows.getString(3));
				assertFalse(rows.next());
			}
		}
	}

	@Test
	void ordinaryAnyKeepsNumericAffinityAndQuotedStrictDoesNotEnableDynamicTyping() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE main.t(id INT, payload ANY DEFAULT 'STRICT') /* STRICT */");
			var table = read(connection, "main");
			assertEquals(DataType.NUMERIC, table.getColumns().get("payload").getDataType());
			var row = table.getRows().newElement();
			row.put("id", 1);
			row.put("payload", "000123");
			var node = DialectHolder.defaultDialect.createSqlFactoryRegistry().createSqlNodes(table, SqlType.INSERT)
					.get(0);
			try (var parameters = node.eval(row); var insert = parameters.createStatement(connection)) {
				parameters.setBind(insert);
				insert.executeUpdate();
			}
			try (var rows = statement.executeQuery("SELECT payload,typeof(payload) FROM main.t")) {
				assertTrue(rows.next());
				assertEquals(123, rows.getInt(1));
				assertEquals("integer", rows.getString(2));
			}
		}
	}

	private Table read(Connection connection, String schema) {
		var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setSchemaName(schema);
		reader.setObjectName("t");
		return reader.getAllFull(connection).get(0);
	}
}
