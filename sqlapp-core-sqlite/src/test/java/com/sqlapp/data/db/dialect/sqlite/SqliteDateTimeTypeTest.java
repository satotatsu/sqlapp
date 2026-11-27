/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlite.
 */
package com.sqlapp.data.db.dialect.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.JdbcTypeHandler;
import com.sqlapp.data.db.dialect.sqlite.util.SqliteJdbcTypeHandlers;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

/** SQLite date and time columns are stored as ISO-8601 text. */
class SqliteDateTimeTypeTest {
	@Test
	void readsDeclaredDateAndTimeTypes() throws Exception {
		var dialect = DialectHolder.defaultDialect;
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(d DATE, dt DATETIME, ts TIMESTAMP(6), tm TIME, n custom_money)");
			var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setSchemaName("main");
			reader.setObjectName("t");
			var table = reader.getAllFull(connection).get(0);
			assertEquals(DataType.DATE, table.getColumns().get("d").getDataType());
			assertEquals(DataType.TIMESTAMP, table.getColumns().get("dt").getDataType());
			assertEquals(DataType.TIMESTAMP, table.getColumns().get("ts").getDataType());
			assertEquals(DataType.TIME, table.getColumns().get("tm").getDataType());
			assertEquals(DataType.NUMERIC, table.getColumns().get("n").getDataType());
			assertEquals("DATETIME", table.getColumns().get("dt").getSpecifics().get("sqlite.declaredType"));
		}
	}

	@Test
	void generatesDateAndTimeDeclarations() throws Exception {
		var dialect = DialectHolder.defaultDialect;
		assertEquals("DATE", type(new Column("V").setDataType(DataType.DATE)));
		assertEquals("TIME", type(new Column("V").setDataType(DataType.TIME)));
		assertEquals("TIMESTAMP", type(new Column("V").setDataType(DataType.TIMESTAMP)));
		for (boolean strict : new boolean[] { false, true }) {
			var table = new Table("DATE_TYPES");
			if (strict)
				table.getSpecifics().put("strict", "true");
			table.getColumns().add(new Column("D").setDataType(DataType.DATE));
			table.getColumns().add(new Column("T").setDataType(DataType.TIME));
			table.getColumns().add(new Column("TS").setDataType(DataType.TIMESTAMP));
			String ddl = dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE).get(0).getSqlText();
			// STRICT tables accept only INT, INTEGER, REAL, TEXT, BLOB and ANY.
			assertEquals(strict, ddl.contains("TEXT"), ddl);
			try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
					var statement = connection.createStatement()) {
				statement.execute(ddl);
			}
		}
	}

	@Test
	void writesIsoTextAndReadsItBack() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			var timestamp = new SqliteJdbcTypeHandlers.TimestampHandler();
			assertStored(connection, timestamp, Timestamp.valueOf("2026-11-17 07:18:00.123"), "2026-11-17 07:18:00.123",
					Timestamp.valueOf("2026-11-17 07:18:00.123"));
			assertStored(connection, timestamp, Timestamp.valueOf("2026-11-17 07:18:00"), "2026-11-17 07:18:00",
					Timestamp.valueOf("2026-11-17 07:18:00"));
			assertStored(connection, timestamp, Timestamp.valueOf("2026-11-17 07:18:00.123456789"),
					"2026-11-17 07:18:00.123456789", Timestamp.valueOf("2026-11-17 07:18:00.123456789"));
			assertStored(connection, new SqliteJdbcTypeHandlers.DateHandler(), java.sql.Date.valueOf("2026-11-17"),
					"2026-11-17", java.sql.Date.valueOf("2026-11-17"));
			assertStored(connection, new SqliteJdbcTypeHandlers.TimeHandler(), LocalTime.of(7, 18), "07:18:00",
					LocalTime.of(7, 18));
			var offset = OffsetDateTime.of(2026, 11, 17, 7, 18, 0, 0, ZoneOffset.ofHours(9));
			assertStored(connection, new SqliteJdbcTypeHandlers.TimestampWithTimeZoneHandler(), offset,
					"2026-11-17 07:18:00+09:00", offset);
			assertStored(connection, new SqliteJdbcTypeHandlers.TimestampWithTimeZoneHandler(),
					Instant.parse("2026-11-17T07:18:00Z"), "2026-11-17 07:18:00+00:00",
					OffsetDateTime.of(2026, 11, 17, 7, 18, 0, 0, ZoneOffset.UTC));
		}
	}

	@Test
	void sqliteDateFunctionsUnderstandStoredText() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE v(value)");
			try (var insert = connection.prepareStatement("INSERT INTO v VALUES (?)")) {
				new SqliteJdbcTypeHandlers.TimestampHandler().setObject(insert, 1,
						Timestamp.valueOf("2026-11-17 07:18:00.123"));
				insert.executeUpdate();
			}
			try (var rs = statement.executeQuery("SELECT date(value), strftime('%H:%M', value) FROM v")) {
				assertTrue(rs.next());
				assertEquals("2026-11-17", rs.getString(1));
				assertEquals("07:18", rs.getString(2));
			}
		}
	}

	@Test
	void readsOtherStorageFormsFoundInExistingDatabases() throws Exception {
		var handler = new SqliteJdbcTypeHandlers.TimestampHandler();
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			assertEquals(Timestamp.from(Instant.ofEpochSecond(1_700_000_000L)),
					read(connection, handler, "1700000000"));
			assertEquals(Timestamp.from(Instant.ofEpochMilli(1_700_000_000_123L)),
					read(connection, handler, "1700000000123"));
			assertEquals(Timestamp.from(Instant.EPOCH), read(connection, handler, "2440587.5"));
			assertEquals(Timestamp.valueOf("2026-11-17 07:18:00"), read(connection, handler, "'2026-11-17T07:18'"));
			assertEquals(Timestamp.valueOf("2026-11-17 00:00:00"), read(connection, handler, "'2026-11-17'"));
			assertEquals(Timestamp.from(Instant.parse("2026-11-17T07:18:00Z")),
					read(connection, handler, "'2026-11-17 07:18:00Z'"));
			assertNull(read(connection, handler, "NULL"));
			assertThrows(SQLException.class, () -> read(connection, handler, "'not a date'"));
		}
	}

	private static String type(Column column) {
		return DialectHolder.defaultDialect.createSqlBuilder().addTypeDefinition(column).toString();
	}

	private static void assertStored(Connection connection, JdbcTypeHandler handler, Object value, String stored,
			Object expected) throws SQLException {
		try (var statement = connection.createStatement()) {
			statement.execute("DROP TABLE IF EXISTS v");
			statement.execute("CREATE TABLE v(value)");
			try (var insert = connection.prepareStatement("INSERT INTO v VALUES (?)")) {
				handler.setObject(insert, 1, value);
				insert.executeUpdate();
			}
			try (var rs = statement.executeQuery("SELECT typeof(value), value, value FROM v")) {
				assertTrue(rs.next());
				assertEquals("text", rs.getString(1));
				assertEquals(stored, rs.getString(2));
				assertEquals(expected, handler.getObject(rs, 3));
			}
		}
	}

	private static Object read(Connection connection, JdbcTypeHandler handler, String literal) throws SQLException {
		try (var statement = connection.createStatement();
				var rs = statement.executeQuery("SELECT " + literal + " AS value")) {
			assertTrue(rs.next());
			return handler.getObject(rs, "value");
		}
	}
}
