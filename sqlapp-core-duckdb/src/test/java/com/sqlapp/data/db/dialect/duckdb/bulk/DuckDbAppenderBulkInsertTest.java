/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.duckdb.DialectHolder;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkInsertMethod;
import com.sqlapp.jdbc.bulk.BulkInsertResolver;
import com.sqlapp.jdbc.bulk.BulkOption;

/** Runs on embedded DuckDB; no external database is used. */
class DuckDbAppenderBulkInsertTest {

	private static final String ALL_TYPES = "(b BOOLEAN, ti TINYINT, si SMALLINT, i INTEGER, bi BIGINT, h HUGEINT,"
			+ " f FLOAT, d DOUBLE, dec DECIMAL(6,2), v VARCHAR, bl BLOB, dt DATE, tm TIME, ts TIMESTAMP,"
			+ " tz TIMESTAMPTZ, u UUID)";

	private static Table allTypes(final String name) {
		final Table table = new Table(name);
		table.setDialect(DialectHolder.defaultDialect);
		table.getColumns().add(new Column("b").setDataType(DataType.BOOLEAN));
		table.getColumns().add(new Column("ti").setDataType(DataType.TINYINT));
		table.getColumns().add(new Column("si").setDataType(DataType.SMALLINT));
		table.getColumns().add(new Column("i").setDataType(DataType.INT));
		table.getColumns().add(new Column("bi").setDataType(DataType.BIGINT));
		table.getColumns().add(new Column("h").setDataType(DataType.HUGEINT));
		table.getColumns().add(new Column("f").setDataType(DataType.REAL));
		table.getColumns().add(new Column("d").setDataType(DataType.DOUBLE));
		table.getColumns().add(new Column("dec").setDataType(DataType.DECIMAL).setLength(6).setScale(2));
		table.getColumns().add(new Column("v").setDataType(DataType.VARCHAR));
		table.getColumns().add(new Column("bl").setDataType(DataType.BLOB));
		table.getColumns().add(new Column("dt").setDataType(DataType.DATE));
		table.getColumns().add(new Column("tm").setDataType(DataType.TIME));
		table.getColumns().add(new Column("ts").setDataType(DataType.TIMESTAMP));
		table.getColumns().add(new Column("tz").setDataType(DataType.TIMESTAMP_WITH_TIMEZONE));
		table.getColumns().add(new Column("u").setDataType(DataType.UUID));
		// Typical values, values of other Java classes, rounding and NULLs.
		addRow(table, true, (byte) 1, (short) 2, 3, 4L, new BigInteger("170141183460469231731687303715884105727"), 1.5f,
				2.25d, new BigDecimal("1234.56"), "text", new byte[] { 1, 2, 3 }, LocalDate.of(2026, 1, 2),
				LocalTime.of(3, 4, 5, 123_456_000), LocalDateTime.of(2026, 1, 2, 3, 4, 5, 123_456_000),
				OffsetDateTime.of(2026, 1, 2, 3, 4, 5, 0, ZoneOffset.ofHours(9)),
				UUID.fromString("00000000-0000-0001-8000-000000000000"));
		addRow(table, "false", 7, 8L, new BigDecimal("2.5"), Integer.valueOf(9), 10L, new BigDecimal("1.25"), 3,
				new BigDecimal("-1.235"), 42, null, java.sql.Date.valueOf("2026-03-04"),
				java.sql.Time.valueOf("05:06:07"), java.sql.Timestamp.valueOf("2026-03-04 05:06:07.5"),
				"2026-03-04T05:06:07+02:00", "ffffffff-ffff-ffff-ffff-ffffffffffff");
		addRow(table, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
		return table;
	}

	private static void addRow(final Table table, final Object... values) {
		final Row row = table.newRow();
		for (int i = 0; i < values.length; i++) {
			row.put(table.getColumns().get(i), values[i]);
		}
		table.getRows().add(row);
	}

	private static void execute(final Connection connection, final String sql) throws SQLException {
		try (var statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}

	private static List<String> rows(final Connection connection, final String sql) throws SQLException {
		final List<String> rows = new ArrayList<>();
		try (var statement = connection.createStatement(); var resultSet = statement.executeQuery(sql)) {
			final int count = resultSet.getMetaData().getColumnCount();
			while (resultSet.next()) {
				final StringBuilder row = new StringBuilder();
				for (int i = 1; i <= count; i++) {
					row.append(i == 1 ? "" : " | ").append(resultSet.getString(i));
				}
				rows.add(row.toString());
			}
		}
		return rows;
	}

	private static List<Column> columns(final Table table) {
		return new ArrayList<>(table.getColumns());
	}

	@Test
	void storesTheSameValuesAsTheJdbcBatch() throws Exception {
		try (Connection connection = DriverManager.getConnection("jdbc:duckdb:")) {
			execute(connection, "SET TimeZone = 'UTC'");
			execute(connection, "CREATE TABLE by_appender " + ALL_TYPES);
			execute(connection, "CREATE TABLE by_batch " + ALL_TYPES);
			final Table appended = allTypes("by_appender");
			assertAppenderSelected(connection, appended);

			assertEquals(3, BulkInsertResolver.execute(connection, appended, BulkOption.defaults()));
			assertEquals(3, BulkInsertResolver.execute(connection, allTypes("by_batch"),
					BulkOption.builder().insertMethod(BulkInsertMethod.JDBC_BATCH).build()));

			// Compare DuckDB's own text form, which keeps microseconds and offsets.
			final String select = "SELECT b::VARCHAR, ti::VARCHAR, si::VARCHAR, i::VARCHAR, bi::VARCHAR, h::VARCHAR,"
					+ " f::VARCHAR, d::VARCHAR, dec::VARCHAR, v, bl::VARCHAR, dt::VARCHAR, tm::VARCHAR, ts::VARCHAR,"
					+ " tz::VARCHAR, u::VARCHAR FROM %s ORDER BY i NULLS LAST";
			final List<String> expected = rows(connection, select.formatted("by_batch"));
			assertEquals(expected, rows(connection, select.formatted("by_appender")));
			if (!DuckDbAppenderBulkInsert.isAvailable()) {
				// Before 1.4 both tables use the batch, and DuckDB 1.0 rounds DECIMAL casts
				// differently; the values below are those of DuckDB 1.4 and later.
				return;
			}
			assertEquals(List.of(
					// Values of other Java classes go through the same column converters.
					"false | 7 | 8 | 2 | 9 | 10 | 1.25 | 3.0 | -1.24 | 42 | null | 2026-03-04 | 05:06:07"
							+ " | 2026-03-04 05:06:07.5 | 2026-03-04 03:06:07+00 | ffffffff-ffff-ffff-ffff-ffffffffffff",
					"true | 1 | 2 | 3 | 4 | 170141183460469231731687303715884105727 | 1.5 | 2.25 | 1234.56 | text"
							+ " | \\x01\\x02\\x03 | 2026-01-02 | 03:04:05 | 2026-01-02 03:04:05.123456"
							+ " | 2026-01-01 18:04:05+00 | 00000000-0000-0001-8000-000000000000",
					"null | null | null | null | null | null | null | null | null | null | null | null | null | null"
							+ " | null | null"),
					expected);
		}
	}

	@Test
	void joinsTheCallerTransaction() throws Exception {
		try (Connection connection = DriverManager.getConnection("jdbc:duckdb:")) {
			execute(connection, "CREATE TABLE item (id INTEGER PRIMARY KEY, name VARCHAR NOT NULL)");
			final Table table = item();
			addRow(table, 1, "a");
			addRow(table, 2, "b");
			connection.setAutoCommit(false);

			assertEquals(2, BulkInsertResolver.execute(connection, table, BulkOption.defaults()));
			assertEquals(List.of("2"), rows(connection, "SELECT count(*) FROM item"));
			connection.rollback();

			assertEquals(List.of("0"), rows(connection, "SELECT count(*) FROM item"));
		}
	}

	@Test
	void reportsConstraintViolations() throws Exception {
		try (Connection connection = DriverManager.getConnection("jdbc:duckdb:")) {
			execute(connection, "CREATE TABLE item (id INTEGER PRIMARY KEY, name VARCHAR NOT NULL)");
			execute(connection, "INSERT INTO item VALUES (1, 'existing')");
			final Table duplicate = item();
			addRow(duplicate, 1, "duplicate");
			assertTrue(assertThrows(SQLException.class,
					() -> BulkInsertResolver.execute(connection, duplicate, BulkOption.defaults())).getMessage()
					.contains("Duplicate key"));

			final Table missing = item();
			addRow(missing, 2, null);
			assertThrows(SQLException.class,
					() -> BulkInsertResolver.execute(connection, missing, BulkOption.defaults()));

			// The model says BIGINT, so the value reaches the INTEGER column unconverted.
			final Table overflow = item();
			overflow.getColumns().get("id").setDataType(DataType.BIGINT);
			addRow(overflow, 3_000_000_000L, "x");
			final SQLException e = assertThrows(SQLException.class,
					() -> BulkInsertResolver.execute(connection, overflow, BulkOption.defaults()));
			assertTrue(e.getMessage().contains(DuckDbAppenderBulkInsert.isAvailable() ? "id INTEGER" : "out of range"),
					e.getMessage());
			assertEquals(List.of("1 | existing"), rows(connection, "SELECT * FROM item"));
		}
	}

	@Test
	void usesTheBatchWhenTheAppenderCannotReproduceIt() throws Exception {
		try (Connection connection = DriverManager.getConnection("jdbc:duckdb:")) {
			execute(connection, "CREATE SEQUENCE item_seq START 100");
			// A column written by its default: appendDefault() stores 0 for nextval.
			execute(connection, "CREATE TABLE with_default (id BIGINT DEFAULT nextval('item_seq'), name VARCHAR)");
			final Table withDefault = new Table("with_default");
			withDefault.setDialect(DialectHolder.defaultDialect);
			withDefault.getColumns().add(new Column("name").setDataType(DataType.VARCHAR));
			addRow(withDefault, "a");
			assertNull(DuckDbAppenderBulkInsert.plan(connection, DialectHolder.defaultDialect, withDefault,
					columns(withDefault)));
			assertEquals(1, BulkInsertResolver.execute(connection, withDefault, BulkOption.defaults()));
			assertEquals(List.of("100 | a"), rows(connection, "SELECT * FROM with_default"));

			// Unsupported type.
			execute(connection, "CREATE TABLE with_interval (id INTEGER, span INTERVAL)");
			final Table withInterval = new Table("with_interval");
			withInterval.setDialect(DialectHolder.defaultDialect);
			withInterval.getColumns().add(new Column("id").setDataType(DataType.INT));
			withInterval.getColumns().add(new Column("span").setDataType(DataType.INTERVAL));
			assertNull(DuckDbAppenderBulkInsert.plan(connection, DialectHolder.defaultDialect, withInterval,
					columns(withInterval)));

			// Missing table: the batch reports the error.
			final Table missing = item();
			missing.setName("missing_table");
			addRow(missing, 1, "a");
			assertNull(
					DuckDbAppenderBulkInsert.plan(connection, DialectHolder.defaultDialect, missing, columns(missing)));
			assertThrows(SQLException.class,
					() -> BulkInsertResolver.execute(connection, missing, BulkOption.defaults()));
		}
	}

	@Test
	void resolvesQuotedNamesAndSchemas() throws Exception {
		try (Connection connection = DriverManager.getConnection("jdbc:duckdb:")) {
			execute(connection, "CREATE SCHEMA mart");
			execute(connection, "CREATE TABLE mart.\"Order Item\" (\"Id\" INTEGER, qty INTEGER)");
			final Table table = new Table("order item");
			table.setSchemaName("MART");
			table.setDialect(DialectHolder.defaultDialect);
			table.getColumns().add(new Column("QTY").setDataType(DataType.INT));
			table.getColumns().add(new Column("id").setDataType(DataType.INT));
			addRow(table, 5, 1);
			assertAppenderSelected(connection, table);

			assertEquals(1, BulkInsertResolver.execute(connection, table, BulkOption.defaults()));

			assertEquals(List.of("1 | 5"), rows(connection, "SELECT * FROM mart.\"Order Item\""));
		}
	}

	@Test
	void skipsGeneratedColumns() throws Exception {
		try (Connection connection = DriverManager.getConnection("jdbc:duckdb:")) {
			execute(connection, "CREATE TABLE line (qty INTEGER,"
					+ " doubled INTEGER GENERATED ALWAYS AS (qty * 2) VIRTUAL, price INTEGER)");
			final Table table = new Table("line");
			table.setDialect(DialectHolder.defaultDialect);
			table.getColumns().add(new Column("qty").setDataType(DataType.INT));
			table.getColumns().add(new Column("price").setDataType(DataType.INT));
			addRow(table, 5, 7);

			// DuckDB 2.0 reports generated columns and uses the Appender; earlier
			// versions do not, so the column looks unwritten and the batch is used.
			assertEquals(1, BulkInsertResolver.execute(connection, table, BulkOption.defaults()));

			assertEquals(List.of("5 | 10 | 7"), rows(connection, "SELECT * FROM line"));
		}
	}

	/**
	 * Drivers before 1.4 have no usable Appender API; the compatibility test tasks
	 * then verify the batch fallback with the same assertions.
	 */
	private static void assertAppenderSelected(final Connection connection, final Table table) throws SQLException {
		final var plan = DuckDbAppenderBulkInsert.plan(connection, DialectHolder.defaultDialect, table, columns(table));
		if (DuckDbAppenderBulkInsert.isAvailable()) {
			assertNotNull(plan, "Appender must be selected");
		} else {
			assertNull(plan);
		}
	}

	private static Table item() {
		final Table table = new Table("item");
		table.setDialect(DialectHolder.defaultDialect);
		table.getColumns().add(new Column("id").setDataType(DataType.INT));
		table.getColumns().add(new Column("name").setDataType(DataType.VARCHAR));
		return table;
	}
}
