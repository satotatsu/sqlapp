/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.duckdb.DialectHolder;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.MigrationSnapshotDefinition;
import com.sqlapp.jdbc.bulk.BulkInsertResolver;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;
import com.sqlapp.jdbc.bulk.BulkUpsertResolver;
import com.sqlapp.jdbc.bulk.MigrationSnapshotExecutionResult;
import com.sqlapp.jdbc.bulk.SetBasedMigrationSnapshotResolver;

class DuckDbBulkTest {

	@Test
	void resolvesDuckDbExecutors() {
		assertInstanceOf(DuckDbBulkInsertExecutor.class, BulkInsertResolver.resolve(DialectHolder.defaultDialect));
		assertInstanceOf(DuckDbBulkUpsertExecutor.class, BulkUpsertResolver.resolve(DialectHolder.duckdb11));
	}

	@Test
	void upsertsWithTypedValues() throws Exception {
		try (Connection connection = DriverManager.getConnection("jdbc:duckdb:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE item (id INTEGER PRIMARY KEY, amount HUGEINT, \"at\" TIME, name VARCHAR)");
			statement.execute("INSERT INTO item VALUES (1, 1, TIME '00:00:00', 'old')");
			final Table table = new Table("item");
			table.setDialect(DialectHolder.defaultDialect);
			table.getColumns().add(new Column("id").setDataType(DataType.INT));
			table.getColumns().add(new Column("amount").setDataType(DataType.HUGEINT));
			table.getColumns().add(new Column("at").setDataType(DataType.TIME));
			table.getColumns().add(new Column("name").setDataType(DataType.VARCHAR));
			table.setPrimaryKey(table.getColumns().get("id"));
			addRow(table, 1, new BigInteger("170141183460469231731687303715884105727"), LocalTime.parse("12:34:56"),
					"new");
			addRow(table, 2, BigInteger.TWO, null, "added");
			assertEquals(2, BulkUpsertResolver.execute(connection, table, BulkUpsertOption.defaults()));
			try (var rows = statement.executeQuery("SELECT id, amount::VARCHAR, \"at\", name FROM item ORDER BY id")) {
				assertTrue(rows.next());
				assertEquals("170141183460469231731687303715884105727", rows.getString(2));
				assertEquals("12:34:56", rows.getString(3));
				assertEquals("new", rows.getString(4));
				assertTrue(rows.next());
				assertEquals("2", rows.getString(2));
				assertNull(rows.getObject(3));
				assertFalse(rows.next());
			}
		}
	}

	private static void addRow(final Table table, final Object... values) {
		final Row row = table.newRow();
		for (int i = 0; i < values.length; i++) {
			row.put(table.getColumns().get(i), values[i]);
		}
		table.getRows().add(row);
	}

	@Test
	void appliesSetBasedSnapshot() throws Exception {
		try (Connection connection = DriverManager.getConnection("jdbc:duckdb:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE customer_history (id INTEGER, name VARCHAR, valid_from TIMESTAMP NOT NULL,"
					+ " valid_to TIMESTAMP, is_current BOOLEAN NOT NULL)");
			statement.execute("INSERT INTO customer_history VALUES (1, NULL, '2026-01-01', NULL, true),"
					+ " (2, 'old', '2026-01-01', NULL, true), (3, 'removed', '2026-01-01', NULL, true)");
			final var definition = new MigrationSnapshotDefinition("customer", "customer_history", List.of("id"),
					List.of("name"), "valid_from", "valid_to", "is_current", true);
			final var executor = SetBasedMigrationSnapshotResolver.resolve(connection);
			assertInstanceOf(DuckDbSetBasedMigrationSnapshotExecutor.class, executor);
			final var result = executor.execute(connection, snapshotTable(), definition,
					Instant.parse("2026-09-16T00:00:00Z"), List.of(row(1, null), row(2, "new"), row(4, "added")), 2);
			assertEquals(new MigrationSnapshotExecutionResult(2, 2, 1), result);
			try (var rows = statement.executeQuery("SELECT COUNT(*) FROM customer_history WHERE valid_to IS NULL")) {
				rows.next();
				assertEquals(3, rows.getInt(1));
			}
		}
	}

	private static Table snapshotTable() {
		final Table table = new Table("customer_history");
		table.getColumns().add(new Column("id").setDataType(DataType.INT));
		table.getColumns().add(new Column("name").setDataType(DataType.VARCHAR));
		table.getColumns().add(new Column("valid_from").setDataType(DataType.TIMESTAMP));
		table.getColumns().add(new Column("valid_to").setDataType(DataType.TIMESTAMP));
		table.getColumns().add(new Column("is_current").setDataType(DataType.BOOLEAN));
		return table;
	}

	private static Map<String, Object> row(final int id, final String name) {
		final Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", id);
		row.put("name", name);
		return row;
	}
}
