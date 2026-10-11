/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.MigrationSnapshotDefinition;
import com.sqlapp.data.schemas.migration.MigrationSnapshotPlanner;

class JdbcBatchMigrationSnapshotExecutorTest {

	@Test
	void closesAndInsertsVersionsUsingReusedBatches() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:scd2_executor", "SA", "")) {
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE CUSTOMER_HISTORY (ID INTEGER NOT NULL, NAME VARCHAR(20), "
						+ "VALID_FROM TIMESTAMP NOT NULL, VALID_TO TIMESTAMP, IS_CURRENT BOOLEAN NOT NULL)");
				statement.execute("INSERT INTO CUSTOMER_HISTORY VALUES "
						+ "(1, 'old', TIMESTAMP '2026-01-01 00:00:00', NULL, TRUE), "
						+ "(2, 'removed', TIMESTAMP '2026-01-01 00:00:00', NULL, TRUE)");
			}
			final var definition = new MigrationSnapshotDefinition("customer", "CUSTOMER_HISTORY", List.of("ID"),
					List.of("NAME"), "VALID_FROM", "VALID_TO", "IS_CURRENT", true);
			final var plan = MigrationSnapshotPlanner.plan(definition, Instant.parse("2026-09-16T00:00:00Z"),
					List.of(row(1, "new"), row(3, "added")), List.of(row(1, "old"), row(2, "removed")));

			final var result = JdbcBatchMigrationSnapshotExecutor.execute(connection, table(), plan, 100);

			assertEquals(2, result.expiredRows());
			assertEquals(2, result.insertedRows());
			try (var statement = connection.createStatement();
					var rs = statement.executeQuery("SELECT COUNT(*) FROM CUSTOMER_HISTORY WHERE IS_CURRENT")) {
				rs.next();
				assertEquals(2, rs.getInt(1));
			}
			try (var statement = connection.createStatement();
					var rs = statement.executeQuery("SELECT NAME FROM CUSTOMER_HISTORY WHERE ID=1 AND IS_CURRENT")) {
				rs.next();
				assertEquals("new", rs.getString(1));
			}
		}
	}

	@Test
	void streamsOrderedRowsWithBoundedBatches() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:scd2_streaming", "SA", "")) {
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE CUSTOMER_HISTORY (ID INTEGER NOT NULL, NAME VARCHAR(20), "
						+ "VALID_FROM TIMESTAMP NOT NULL, VALID_TO TIMESTAMP, IS_CURRENT BOOLEAN NOT NULL)");
				statement.execute("INSERT INTO CUSTOMER_HISTORY VALUES "
						+ "(1, 'same', TIMESTAMP '2026-01-01 00:00:00', NULL, TRUE), "
						+ "(2, 'old', TIMESTAMP '2026-01-01 00:00:00', NULL, TRUE), "
						+ "(3, 'removed', TIMESTAMP '2026-01-01 00:00:00', NULL, TRUE)");
			}
			final var definition = new MigrationSnapshotDefinition("customer", "CUSTOMER_HISTORY", List.of("ID"),
					List.of("NAME"), "VALID_FROM", "VALID_TO", "IS_CURRENT", true);

			final var result = JdbcBatchMigrationSnapshotExecutor.executeStreaming(connection, table(), definition,
					Instant.parse("2026-09-16T00:00:00Z"), List.of(row(1, "same"), row(2, "new"), row(4, "added")),
					List.of(row(1, "same"), row(2, "old"), row(3, "removed")), 1);

			assertEquals(new MigrationSnapshotExecutionResult(2, 2, 1), result);
			try (var statement = connection.createStatement();
					var rs = statement.executeQuery("SELECT COUNT(*) FROM CUSTOMER_HISTORY WHERE IS_CURRENT")) {
				rs.next();
				assertEquals(3, rs.getInt(1));
			}
		}
	}

	@Test
	void streamsDirectlyFromSourceAndCurrentTargetQueries() throws Exception {
		try (var source = DriverManager.getConnection("jdbc:hsqldb:mem:scd2_jdbc_source", "SA", "");
				var target = DriverManager.getConnection("jdbc:hsqldb:mem:scd2_jdbc_target", "SA", "")) {
			try (var statement = source.createStatement()) {
				statement.execute("CREATE TABLE CUSTOMER (ID INTEGER NOT NULL, NAME VARCHAR(20))");
				statement.execute("INSERT INTO CUSTOMER VALUES (1, 'new'), (3, 'added')");
			}
			try (var statement = target.createStatement()) {
				statement.execute("CREATE TABLE CUSTOMER_HISTORY (ID INTEGER NOT NULL, NAME VARCHAR(20), "
						+ "VALID_FROM TIMESTAMP NOT NULL, VALID_TO TIMESTAMP, IS_CURRENT BOOLEAN NOT NULL)");
				statement.execute("INSERT INTO CUSTOMER_HISTORY VALUES "
						+ "(1, 'old', TIMESTAMP '2026-01-01 00:00:00', NULL, TRUE), "
						+ "(2, 'removed', TIMESTAMP '2026-01-01 00:00:00', NULL, TRUE)");
			}
			final var definition = new MigrationSnapshotDefinition("customer", "CUSTOMER_HISTORY", List.of("ID"),
					List.of("NAME"), "VALID_FROM", "VALID_TO", "IS_CURRENT", true);

			final var result = JdbcStreamingMigrationSnapshotExecutor.execute(source, target, sourceTable(), table(),
					definition, Instant.parse("2026-09-16T00:00:00Z"), 50, 50);

			assertEquals(new MigrationSnapshotExecutionResult(2, 2, 0), result);
			try (var statement = target.createStatement();
					var rs = statement.executeQuery("SELECT COUNT(*) FROM CUSTOMER_HISTORY WHERE VALID_TO IS NULL")) {
				rs.next();
				assertEquals(2, rs.getInt(1));
			}
		}
	}

	@Test
	void rollsBackPlannedBatchWhenCommitGuardFails() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:scd2_guard_plan", "SA", "")) {
			createSingleCurrentRow(connection);
			final var plan = MigrationSnapshotPlanner.plan(definition(), Instant.parse("2026-09-16T00:00:00Z"),
					List.of(row(1, "new")), List.of(row(1, "old")));

			final var error = assertThrows(SQLException.class,
					() -> JdbcBatchMigrationSnapshotExecutor.execute(connection, table(), plan, 100, () -> {
						throw new SQLException("lease lost");
					}));

			assertEquals("lease lost", error.getMessage());
			assertOriginalRow(connection);
		}
	}

	@Test
	void rollsBackStreamingBatchWhenCommitGuardFails() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:scd2_guard_stream", "SA", "")) {
			createSingleCurrentRow(connection);

			final var error = assertThrows(SQLException.class,
					() -> JdbcBatchMigrationSnapshotExecutor.executeStreaming(connection, table(), definition(),
							Instant.parse("2026-09-16T00:00:00Z"), List.of(row(1, "new")), List.of(row(1, "old")), 100,
							() -> {
								throw new SQLException("lease lost");
							}));

			assertEquals("lease lost", error.getMessage());
			assertOriginalRow(connection);
		}
	}

	@Test
	void rejectsAnEffectiveTimestampThatCannotCloseTheCurrentInterval() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:scd2_invalid_effective_at", "SA", "")) {
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE CUSTOMER_HISTORY (ID INTEGER NOT NULL, NAME VARCHAR(20), "
						+ "VALID_FROM TIMESTAMP NOT NULL, VALID_TO TIMESTAMP, IS_CURRENT BOOLEAN NOT NULL)");
				statement.execute("INSERT INTO CUSTOMER_HISTORY VALUES "
						+ "(1, 'old', TIMESTAMP '2026-09-16 00:00:00', NULL, TRUE)");
			}
			final var definition = definition();
			final var plan = MigrationSnapshotPlanner.plan(definition, Instant.parse("2025-09-16T00:00:00Z"),
					List.of(row(1, "new")), List.of(row(1, "old")));

			final var error = assertThrows(SQLException.class,
					() -> JdbcBatchMigrationSnapshotExecutor.execute(connection, table(), plan, 100));
			assertEquals("Snapshot target violates temporal or current-marker invariants", error.getMessage());
			assertEquals(1, count(connection, "SELECT COUNT(*) FROM CUSTOMER_HISTORY WHERE VALID_TO IS NULL"));
		}
	}

	@Test
	void rejectsInconsistentCurrentMarkersBeforeMutation() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:scd2_invalid_marker", "SA", "")) {
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE CUSTOMER_HISTORY (ID INTEGER NOT NULL, NAME VARCHAR(20), "
						+ "VALID_FROM TIMESTAMP NOT NULL, VALID_TO TIMESTAMP, IS_CURRENT BOOLEAN)");
				statement.execute("INSERT INTO CUSTOMER_HISTORY VALUES "
						+ "(1, 'old', TIMESTAMP '2026-01-01 00:00:00', NULL, FALSE)");
			}
			final var definition = definition();
			final var plan = MigrationSnapshotPlanner.plan(definition, Instant.parse("2026-09-16T00:00:00Z"),
					List.of(row(1, "new")), List.of(row(1, "old")));

			final var error = assertThrows(SQLException.class,
					() -> JdbcBatchMigrationSnapshotExecutor.execute(connection, table(), plan, 100));
			assertEquals("Snapshot target violates temporal or current-marker invariants", error.getMessage());
			assertEquals(1, count(connection, "SELECT COUNT(*) FROM CUSTOMER_HISTORY WHERE NAME='old'"));
		}
	}

	@Test
	void supportsDefinitionsWithoutACurrentMarker() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:scd2_without_marker", "SA", "")) {
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE CUSTOMER_HISTORY (ID INTEGER NOT NULL, NAME VARCHAR(20), "
						+ "VALID_FROM TIMESTAMP NOT NULL, VALID_TO TIMESTAMP)");
				statement.execute(
						"INSERT INTO CUSTOMER_HISTORY VALUES " + "(1, 'old', TIMESTAMP '2026-01-01 00:00:00', NULL)");
			}
			final var definition = new MigrationSnapshotDefinition("customer", "CUSTOMER_HISTORY", List.of("ID"),
					List.of("NAME"), "VALID_FROM", "VALID_TO", null, true);
			final var target = new Table("CUSTOMER_HISTORY").setSchemaName("PUBLIC");
			target.getColumns().add(new Column("ID").setDataType(DataType.INT));
			target.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR));
			target.getColumns().add(new Column("VALID_FROM").setDataType(DataType.TIMESTAMP));
			target.getColumns().add(new Column("VALID_TO").setDataType(DataType.TIMESTAMP));
			final var plan = MigrationSnapshotPlanner.plan(definition, Instant.parse("2026-09-16T00:00:00Z"),
					List.of(row(1, "new")), List.of(row(1, "old")));

			assertEquals(new MigrationSnapshotExecutionResult(1, 1, 0),
					JdbcBatchMigrationSnapshotExecutor.execute(connection, target, plan, 100));
			assertEquals(1, count(connection, "SELECT COUNT(*) FROM CUSTOMER_HISTORY WHERE VALID_TO IS NULL"));
		}
	}

	@Test
	void rejectsPartiallyNullCompositeCurrentKeys() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:scd2_composite_null", "SA", "")) {
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE CUSTOMER_HISTORY (ID INTEGER, REGION VARCHAR(10), NAME VARCHAR(20), "
						+ "VALID_FROM TIMESTAMP NOT NULL, VALID_TO TIMESTAMP, IS_CURRENT BOOLEAN NOT NULL)");
				statement.execute("INSERT INTO CUSTOMER_HISTORY VALUES "
						+ "(1, NULL, 'old', TIMESTAMP '2026-01-01 00:00:00', NULL, TRUE)");
			}
			final var definition = new MigrationSnapshotDefinition("customer", "CUSTOMER_HISTORY",
					List.of("ID", "REGION"), List.of("NAME"), "VALID_FROM", "VALID_TO", "IS_CURRENT", true);
			final var target = new Table("CUSTOMER_HISTORY").setSchemaName("PUBLIC");
			target.getColumns().add(new Column("ID").setDataType(DataType.INT));
			target.getColumns().add(new Column("REGION").setDataType(DataType.VARCHAR));
			target.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR));
			target.getColumns().add(new Column("VALID_FROM").setDataType(DataType.TIMESTAMP));
			target.getColumns().add(new Column("VALID_TO").setDataType(DataType.TIMESTAMP));
			target.getColumns().add(new Column("IS_CURRENT").setDataType(DataType.BOOLEAN));
			final var plan = MigrationSnapshotPlanner.plan(definition, Instant.parse("2026-09-16T00:00:00Z"), List.of(),
					List.of());

			final var error = assertThrows(SQLException.class,
					() -> JdbcBatchMigrationSnapshotExecutor.execute(connection, target, plan, 100));
			assertEquals("current target snapshot contains duplicate or null snapshot keys", error.getMessage());
		}
	}

	private static MigrationSnapshotDefinition definition() {
		return new MigrationSnapshotDefinition("customer", "CUSTOMER_HISTORY", List.of("ID"), List.of("NAME"),
				"VALID_FROM", "VALID_TO", "IS_CURRENT", true);
	}

	private static int count(final java.sql.Connection connection, final String sql) throws SQLException {
		try (var statement = connection.createStatement(); var resultSet = statement.executeQuery(sql)) {
			resultSet.next();
			return resultSet.getInt(1);
		}
	}

	private static void createSingleCurrentRow(final java.sql.Connection connection) throws SQLException {
		try (var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE CUSTOMER_HISTORY (ID INTEGER NOT NULL, NAME VARCHAR(20), "
					+ "VALID_FROM TIMESTAMP NOT NULL, VALID_TO TIMESTAMP, IS_CURRENT BOOLEAN NOT NULL)");
			statement.execute(
					"INSERT INTO CUSTOMER_HISTORY VALUES " + "(1, 'old', TIMESTAMP '2026-01-01 00:00:00', NULL, TRUE)");
		}
	}

	private static void assertOriginalRow(final java.sql.Connection connection) throws SQLException {
		assertEquals(1, count(connection,
				"SELECT COUNT(*) FROM CUSTOMER_HISTORY WHERE ID=1 AND NAME='old' AND VALID_TO IS NULL AND IS_CURRENT"));
		assertEquals(0, count(connection, "SELECT COUNT(*) FROM CUSTOMER_HISTORY WHERE NAME='new'"));
	}

	private static Table table() {
		final Table table = new Table("CUSTOMER_HISTORY").setSchemaName("PUBLIC");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR).setLength(20));
		table.getColumns().add(new Column("VALID_FROM").setDataType(DataType.TIMESTAMP).setNotNull(true));
		table.getColumns().add(new Column("VALID_TO").setDataType(DataType.TIMESTAMP));
		table.getColumns().add(new Column("IS_CURRENT").setDataType(DataType.BOOLEAN).setNotNull(true));
		return table;
	}

	private static Table sourceTable() {
		final Table table = new Table("CUSTOMER").setSchemaName("PUBLIC");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR).setLength(20));
		return table;
	}

	private static Map<String, Object> row(final int id, final String name) {
		final Map<String, Object> row = new LinkedHashMap<>();
		row.put("ID", id);
		row.put("NAME", name);
		return row;
	}
}
