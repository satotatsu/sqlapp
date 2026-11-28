/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.schemas.migration.MigrationModel;
import com.sqlapp.data.schemas.migration.MigrationModel.Materialization;
import com.sqlapp.data.schemas.migration.MigrationModelSource;
import com.sqlapp.data.schemas.migration.MigrationModelUnitTest.Fixture;
import com.sqlapp.jdbc.bulk.MigrationModelExecutor;
import com.sqlapp.jdbc.bulk.MigrationModelExecutorResolver;

class DuckDbMigrationModelExecutorTest {

	private static List<List<Object>> query(final Connection connection, final String sql) throws SQLException {
		final List<List<Object>> rows = new ArrayList<>();
		try (var statement = connection.createStatement(); var resultSet = statement.executeQuery(sql)) {
			final int count = resultSet.getMetaData().getColumnCount();
			while (resultSet.next()) {
				final List<Object> row = new ArrayList<>();
				for (int i = 1; i <= count; i++) {
					row.add(resultSet.getObject(i));
				}
				rows.add(row);
			}
		}
		return rows;
	}

	private static String type(final Connection connection, final String name) throws SQLException {
		return (String) query(connection,
				"SELECT table_type FROM information_schema.tables WHERE table_name = '" + name + "'").get(0).get(0);
	}

	@Test
	void replacesRelationsAndSwitchesBetweenViewAndTable() throws Exception {
		try (Connection connection = DriverManager.getConnection("jdbc:duckdb:")) {
			final MigrationModelExecutor executor = MigrationModelExecutorResolver.resolve(connection);
			assertInstanceOf(DuckDbMigrationModelExecutor.class, executor);
			connection.setAutoCommit(false);

			executor.materialize(connection, new MigrationModel("Order", "SELECT 1 AS id", Materialization.VIEW),
					"SELECT 1 AS id");
			assertEquals("VIEW", type(connection, "Order"));
			executor.materialize(connection, new MigrationModel("Order", "SELECT 2 AS id", Materialization.VIEW),
					"SELECT 2 AS id");
			assertEquals(List.of(List.of(2)), query(connection, "SELECT id FROM \"Order\""));

			final MigrationModel table = new MigrationModel("Order", null, "unused", Materialization.TABLE,
					Map.of("minimum", 2));
			executor.materialize(connection, table,
					"SELECT * FROM (VALUES (1), (2), (3)) AS v(id) WHERE id >= /*minimum*/0");
			assertEquals("BASE TABLE", type(connection, "Order"));
			assertEquals(List.of(List.of(2), List.of(3)), query(connection, "SELECT id FROM \"Order\" ORDER BY id"));
			executor.materialize(connection, table, "SELECT 9 AS id");
			assertEquals(List.of(List.of(9)), query(connection, "SELECT id FROM \"Order\""));

			executor.materialize(connection, new MigrationModel("Order", "SELECT 5 AS id", Materialization.VIEW),
					"SELECT 5 AS id");
			assertEquals("VIEW", type(connection, "Order"));

			final MigrationModel inSchema = new MigrationModel("customer", "mart", "unused", Materialization.TABLE,
					Map.of());
			assertEquals("\"mart\".\"customer\"", executor.relation(inSchema));
			executor.materialize(connection, inSchema, "SELECT 'a' AS name");
			connection.commit();
			assertEquals(List.of(List.of("a")), query(connection, "SELECT name FROM mart.customer"));
		}
	}

	@Test
	void readsFileSourcesAndFixtures(@TempDir final Path directory) throws Exception {
		final Path csv = directory.resolve("it's data.csv");
		Files.writeString(csv, "id,name\n1,alice\n2,bob\n", StandardCharsets.UTF_8);
		try (Connection connection = DriverManager.getConnection("jdbc:duckdb:")) {
			final MigrationModelExecutor executor = MigrationModelExecutorResolver.resolve(connection);

			final String file = executor.sourceRelation(
					MigrationModelSource.file("raw", csv.toAbsolutePath().toString().replace('\\', '/'), null));
			assertEquals(List.of(Arrays.asList(1L, "alice"), Arrays.asList(2L, "bob")),
					query(connection, "SELECT id, name FROM " + file + " ORDER BY id"));
			assertEquals("\"main\".\"Raw Table\"",
					executor.sourceRelation(MigrationModelSource.table("raw", "main", "Raw Table")));
			assertThrows(IllegalArgumentException.class,
					() -> executor.sourceRelation(MigrationModelSource.file("raw", "data.xlsx", null)));

			final String rows = executor.fixtureRelation(Fixture.rows(List.of("id", "name", "active"),
					List.of(Arrays.asList(1, "o'neil", true), Arrays.asList(2, null, false))));
			assertEquals(List.of(Arrays.asList(1, "o'neil", true), Arrays.asList(2, null, false)),
					query(connection, "SELECT id, name, active FROM " + rows + " AS f ORDER BY id"));
			final String empty = executor.fixtureRelation(Fixture.rows(List.of("id", "name"), List.of()));
			assertEquals(List.of(List.of(0L)), query(connection, "SELECT count(id) FROM " + empty + " AS f"));
			final String sql = executor.fixtureRelation(Fixture.sql("SELECT DATE '2026-01-02' AS d"));
			assertEquals(List.of(List.of(java.time.LocalDate.of(2026, 1, 2))),
					query(connection, "SELECT d FROM " + sql + " AS f"));
		}
	}

	@Test
	void createsDeclaredTablesAndRejectsRowsThatDoNotFit() throws Exception {
		final var columns = List.of(
				new com.sqlapp.data.schemas.migration.MigrationModelColumn("ID",
						com.sqlapp.data.db.datatype.DataType.INT, null, null, false),
				new com.sqlapp.data.schemas.migration.MigrationModelColumn("NAME",
						com.sqlapp.data.db.datatype.DataType.VARCHAR, 5L, null, true),
				new com.sqlapp.data.schemas.migration.MigrationModelColumn("AMOUNT",
						com.sqlapp.data.db.datatype.DataType.DECIMAL, 6L, 2, false));
		final MigrationModel model = new MigrationModel("customer", null, "unused", Materialization.TABLE,
				Map.of("minimum", 1), columns, List.of("ID"));
		try (Connection connection = DriverManager.getConnection("jdbc:duckdb:")) {
			final MigrationModelExecutor executor = MigrationModelExecutorResolver.resolve(connection);
			connection.setAutoCommit(false);
			// Result columns may come in any order and case.
			executor.materialize(connection, model, "SELECT amount, name, id FROM (VALUES"
					+ " (1, 'alice', 1.5), (2, 'bob', NULL), (0, 'x', 9)) AS v(id, name, amount) WHERE id >= /*minimum*/0");
			connection.commit();
			assertEquals(
					List.of(Arrays.asList(1, "alice", new java.math.BigDecimal("1.50")), Arrays.asList(2, "bob", null)),
					query(connection, "SELECT * FROM customer ORDER BY ID"));
			assertEquals(
					List.of(List.of("ID", "INTEGER", "NO"), List.of("NAME", "VARCHAR", "NO"),
							List.of("AMOUNT", "DECIMAL(6,2)", "YES")),
					query(connection, "SELECT column_name, data_type, is_nullable FROM information_schema.columns"
							+ " WHERE table_name = 'customer' ORDER BY ordinal_position"));
			assertEquals(List.of(List.of("PRIMARY KEY")),
					query(connection, "SELECT constraint_type FROM duckdb_constraints() WHERE table_name = 'customer'"
							+ " AND constraint_type = 'PRIMARY KEY'"));

			final String[] invalid = { "SELECT 1 AS ID, 'toolong' AS NAME, 1 AS AMOUNT", // length
					"SELECT 1 AS ID, NULL AS NAME, 1 AS AMOUNT", // NOT NULL
					"SELECT * FROM (VALUES (1, 'a', 1), (1, 'b', 2)) AS v(ID, NAME, AMOUNT)", // primary key
					"SELECT 1 AS ID, 'a' AS NAME, 10000 AS AMOUNT", // precision
					"SELECT 'x' AS ID, 'a' AS NAME, 1 AS AMOUNT", // cast
					"SELECT 1 AS ID, 'a' AS NAME", // missing column
					"SELECT 1 AS ID, 'a' AS NAME, 1 AS AMOUNT, 2 AS EXTRA" }; // undeclared column
			for (final String sql : invalid) {
				assertThrows(Exception.class, () -> executor.materialize(connection, model, sql), sql);
				connection.rollback();
			}
			final var columnMismatch = assertThrows(IllegalArgumentException.class,
					() -> executor.materialize(connection, model, "SELECT 1 AS ID, 'a' AS NAME, 2 AS EXTRA"));
			assertTrue(columnMismatch.getMessage().contains("Missing: [AMOUNT]"), columnMismatch.getMessage());
			assertTrue(columnMismatch.getMessage().contains("Not declared: [EXTRA]"), columnMismatch.getMessage());
			connection.rollback();
			// The committed table is unchanged.
			assertEquals(List.of(List.of(2L)), query(connection, "SELECT count(*) FROM customer"));
		}
	}

	@Test
	void rejectsValuesThatTheDeclaredTypeWouldChange() throws Exception {
		final var columns = List.of(
				new com.sqlapp.data.schemas.migration.MigrationModelColumn("ID",
						com.sqlapp.data.db.datatype.DataType.INT, null, null, false),
				new com.sqlapp.data.schemas.migration.MigrationModelColumn("AMOUNT",
						com.sqlapp.data.db.datatype.DataType.DECIMAL, 12L, 2, false),
				new com.sqlapp.data.schemas.migration.MigrationModelColumn("DAY",
						com.sqlapp.data.db.datatype.DataType.DATE, null, null, false));
		final MigrationModel model = new MigrationModel("payment", null, "unused", Materialization.TABLE, Map.of(),
				columns, List.of("ID"));
		try (Connection connection = DriverManager.getConnection("jdbc:duckdb:")) {
			final MigrationModelExecutor executor = MigrationModelExecutorResolver.resolve(connection);
			connection.setAutoCommit(false);
			// Lossless conversions are accepted: wider integers, fewer fraction digits,
			// dates as text.
			executor.materialize(connection, model, "SELECT 1::BIGINT AS ID, 1.5::DOUBLE AS AMOUNT,"
					+ " '2026-11-20' AS DAY UNION ALL SELECT 2, 3.10, NULL");
			connection.commit();
			assertEquals(
					List.of(Arrays.asList(1, new java.math.BigDecimal("1.50"), "2026-11-20"),
							Arrays.asList(2, new java.math.BigDecimal("3.10"), null)),
					query(connection, "SELECT ID, AMOUNT, CAST(DAY AS VARCHAR) FROM payment ORDER BY ID"));

			final Map<String, String> changing = Map.of( //
					"SELECT 1 AS ID, 12.345 AS AMOUNT, NULL::DATE AS DAY",
					"AMOUNT (1 rows, DECIMAL(5,3) to DECIMAL(12,2))",
					"SELECT 1 AS ID, 0.1::DOUBLE + 0.2::DOUBLE AS AMOUNT, NULL::DATE AS DAY", "AMOUNT (1 rows",
					"SELECT 1 AS ID, 1e11::DOUBLE AS AMOUNT, NULL::DATE AS DAY", "AMOUNT (1 rows", // overflow
					"SELECT 1 AS ID, 1 AS AMOUNT, TIMESTAMP '2026-11-20 10:00:00' AS DAY", "DAY (1 rows",
					"SELECT 1 AS ID, 1 AS AMOUNT, '20261120' AS DAY", "DAY (1 rows", // not a date
					"SELECT 1.5 AS ID, 1 AS AMOUNT, NULL::DATE AS DAY", "ID (1 rows",
					"SELECT 3000000000 AS ID, 1 AS AMOUNT, NULL::DATE AS DAY", "ID (1 rows");
			for (final Map.Entry<String, String> entry : changing.entrySet()) {
				final var e = assertThrows(IllegalArgumentException.class,
						() -> executor.materialize(connection, model, entry.getKey()), entry.getKey());
				assertTrue(e.getMessage().contains(entry.getValue()), e.getMessage());
				connection.rollback();
			}
			// Values are not written to the message.
			final var rounded = assertThrows(IllegalArgumentException.class, () -> executor.materialize(connection,
					model, "SELECT 1 AS ID, 12.345 AS AMOUNT, NULL::DATE AS DAY"));
			assertTrue(!rounded.getMessage().contains("12.345") && rounded.getMessage().contains("ROUND"),
					rounded.getMessage());
			connection.rollback();
			// The committed table is unchanged.
			assertEquals(List.of(List.of(2L)), query(connection, "SELECT count(*) FROM payment"));

			// An explicit conversion in the model SQL is accepted.
			executor.materialize(connection, model,
					"SELECT 1 AS ID, ROUND(12.345, 2) AS AMOUNT, CAST(TIMESTAMP '2026-11-20 10:00:00' AS DATE) AS DAY");
			connection.commit();
			assertEquals(List.of(Arrays.asList(1, new java.math.BigDecimal("12.35"), "2026-11-20")),
					query(connection, "SELECT ID, AMOUNT, CAST(DAY AS VARCHAR) FROM payment"));
		}
	}

	@Test
	void runsTheModelQueryOnceForCheckAndInsert() throws Exception {
		final var columns = List.of(new com.sqlapp.data.schemas.migration.MigrationModelColumn("ID",
				com.sqlapp.data.db.datatype.DataType.INT, null, null, false));
		final MigrationModel model = new MigrationModel("numbered", null, "unused", Materialization.TABLE, Map.of(),
				columns, List.of());
		try (Connection connection = DriverManager.getConnection("jdbc:duckdb:")) {
			final MigrationModelExecutor executor = MigrationModelExecutorResolver.resolve(connection);
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE SEQUENCE numbers");
			}
			connection.setAutoCommit(false);
			executor.materialize(connection, model, "SELECT nextval('numbers') AS ID FROM range(3)");
			connection.commit();
			assertEquals(List.of(List.of(1), List.of(2), List.of(3)),
					query(connection, "SELECT ID FROM numbered ORDER BY ID"));
			assertEquals(List.of(List.of(3L)), query(connection, "SELECT currval('numbers')"));
		}
	}

	@Test
	void mergesIncrementalRowsAndRecordsTheFingerprint() throws Exception {
		final var columns = List.of(
				new com.sqlapp.data.schemas.migration.MigrationModelColumn("ID",
						com.sqlapp.data.db.datatype.DataType.INT, null, null, false),
				new com.sqlapp.data.schemas.migration.MigrationModelColumn("NAME",
						com.sqlapp.data.db.datatype.DataType.VARCHAR, 10L, null, false));
		final MigrationModel model = new MigrationModel("customer", null, "unused", Materialization.INCREMENTAL,
				Map.of(), columns, List.of("ID"));
		try (Connection connection = DriverManager.getConnection("jdbc:duckdb:")) {
			final MigrationModelExecutor executor = MigrationModelExecutorResolver.resolve(connection);
			assertEquals(new MigrationModelExecutor.IncrementalState(false, null),
					executor.incrementalState(connection, model));
			connection.setAutoCommit(false);
			executor.materializeIncremental(connection, model, "SELECT 1 AS ID, 'a' AS NAME UNION ALL SELECT 2, 'b'",
					false, "sha256:first");
			connection.commit();
			assertEquals(new MigrationModelExecutor.IncrementalState(true, "sha256:first"),
					executor.incrementalState(connection, model));
			// isIncremental selects the rows to merge: one update, one insert.
			executor.materializeIncremental(connection, model,
					"SELECT * FROM (SELECT 2 AS ID, 'B' AS NAME UNION ALL SELECT 3, 'c')"
							+ " /*if !isIncremental*/ WHERE FALSE /*end*/",
					true, "sha256:first");
			connection.commit();
			assertEquals(List.of(Arrays.asList(1, "a"), Arrays.asList(2, "B"), Arrays.asList(3, "c")),
					query(connection, "SELECT * FROM customer ORDER BY ID"));
			final var e = assertThrows(IllegalArgumentException.class,
					() -> executor.materializeIncremental(connection, model,
							"SELECT 4 AS ID, 'x' AS NAME UNION ALL SELECT 4, 'y' UNION ALL SELECT NULL, 'z'", true,
							"sha256:first"));
			assertTrue(e.getMessage().contains("1 rows with a NULL key, 1 duplicated keys"), e.getMessage());
			connection.rollback();
			assertEquals(List.of(List.of(3L)), query(connection, "SELECT count(*) FROM customer"));
			// A table without a recorded fingerprint, or a view, has none to compare.
			try (var statement = connection.createStatement()) {
				statement.execute("COMMENT ON TABLE customer IS 'other'");
			}
			connection.commit();
			assertEquals(new MigrationModelExecutor.IncrementalState(true, null),
					executor.incrementalState(connection, model));
		}
	}
}
