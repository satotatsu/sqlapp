/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.MigrationSnapshotDefinition;
import com.sqlapp.jdbc.bulk.MigrationSnapshotExecutionResult;
import com.sqlapp.jdbc.bulk.SetBasedMigrationSnapshotResolver;

class HsqlSetBasedMigrationSnapshotExecutorTest {

	@Test
	void stagesOnceAndAppliesSetBasedUpdateAndInsert() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:hsql_set_scd2", "SA", "")) {
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

			final var result = SetBasedMigrationSnapshotResolver.resolve(connection).execute(connection, table(),
					definition, Instant.parse("2026-09-16T00:00:00Z"),
					List.of(row(1, "same"), row(2, "new"), row(4, "added")), 100);

			assertEquals(new MigrationSnapshotExecutionResult(2, 2, 1), result);
			try (var statement = connection.createStatement();
					var rs = statement.executeQuery("SELECT COUNT(*) FROM CUSTOMER_HISTORY WHERE VALID_TO IS NULL")) {
				rs.next();
				assertEquals(3, rs.getInt(1));
			}
			try (var statement = connection.createStatement();
					var rs = statement
							.executeQuery("SELECT NAME FROM CUSTOMER_HISTORY WHERE ID=2 AND VALID_TO IS NULL")) {
				rs.next();
				assertEquals("new", rs.getString(1));
			}
		}
	}

	@Test
	void rollsBackExpiredRowsWhenTheSetBasedInsertFails() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:hsql_set_scd2_rollback", "SA", "")) {
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE CUSTOMER_HISTORY (ID INTEGER NOT NULL, "
						+ "NAME VARCHAR(20) CHECK (NAME <> 'forbidden'), "
						+ "VALID_FROM TIMESTAMP NOT NULL, VALID_TO TIMESTAMP, IS_CURRENT BOOLEAN NOT NULL)");
				statement.execute("INSERT INTO CUSTOMER_HISTORY VALUES "
						+ "(1, 'old', TIMESTAMP '2026-01-01 00:00:00', NULL, TRUE)");
			}
			final var definition = new MigrationSnapshotDefinition("customer", "CUSTOMER_HISTORY", List.of("ID"),
					List.of("NAME"), "VALID_FROM", "VALID_TO", "IS_CURRENT", true);

			assertThrows(java.sql.SQLException.class,
					() -> SetBasedMigrationSnapshotResolver.resolve(connection).execute(connection, table(), definition,
							Instant.parse("2026-09-16T00:00:00Z"), List.of(row(1, "forbidden")), 100));

			assertTrue(connection.getAutoCommit());
			try (var statement = connection.createStatement();
					var rs = statement
							.executeQuery("SELECT NAME, VALID_TO, IS_CURRENT FROM CUSTOMER_HISTORY WHERE ID=1")) {
				assertTrue(rs.next());
				assertEquals("old", rs.getString(1));
				assertNull(rs.getTimestamp(2));
				assertTrue(rs.getBoolean(3));
				assertFalse(rs.next());
			}
			try (var statement = connection.createStatement();
					var rs = statement.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
							+ "WHERE TABLE_NAME LIKE 'SQLAPP_SCD2_%'")) {
				rs.next();
				assertEquals(0, rs.getInt(1));
			}
		}
	}

	@Test
	void rollsBackSetBasedChangesWhenCommitGuardFails() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:hsql_set_scd2_guard", "SA", "")) {
			createHistory(connection, "(1, 'old', TIMESTAMP '2026-01-01 00:00:00', NULL, TRUE)");

			final var error = assertThrows(java.sql.SQLException.class,
					() -> SetBasedMigrationSnapshotResolver.resolve(connection).execute(connection, table(),
							definition(), Instant.parse("2026-09-16T00:00:00Z"), List.of(row(1, "new")), 100, () -> {
								throw new java.sql.SQLException("lease lost");
							}));

			assertEquals("lease lost", error.getMessage());
			assertCurrentRows(connection, 1, "old");
			try (var statement = connection.createStatement();
					var rs = statement.executeQuery("SELECT COUNT(*) FROM CUSTOMER_HISTORY WHERE NAME='new'")) {
				rs.next();
				assertEquals(0, rs.getInt(1));
			}
		}
	}

	@Test
	void doesNotCommitACallerOwnedTransactionDuringStageCleanup() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:hsql_set_scd2_caller_tx", "SA", "")) {
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE CALLER_WORK (ID INTEGER)");
				statement.execute("CREATE TABLE CUSTOMER_HISTORY (ID INTEGER NOT NULL, NAME VARCHAR(20), "
						+ "VALID_FROM TIMESTAMP NOT NULL, VALID_TO TIMESTAMP, IS_CURRENT BOOLEAN NOT NULL)");
				statement.execute("INSERT INTO CUSTOMER_HISTORY VALUES "
						+ "(1, 'old', TIMESTAMP '2026-01-01 00:00:00', NULL, TRUE)");
			}
			connection.setAutoCommit(false);
			try (var statement = connection.createStatement()) {
				statement.execute("INSERT INTO CALLER_WORK VALUES (1)");
			}
			final var definition = new MigrationSnapshotDefinition("customer", "CUSTOMER_HISTORY", List.of("ID"),
					List.of("NAME"), "VALID_FROM", "VALID_TO", "IS_CURRENT", true);

			SetBasedMigrationSnapshotResolver.resolve(connection).execute(connection, table(), definition,
					Instant.parse("2026-09-16T00:00:00Z"), List.of(row(1, "new")), 100);

			assertFalse(connection.getAutoCommit());
			connection.rollback();
			try (var statement = connection.createStatement();
					var rs = statement
							.executeQuery("SELECT NAME, VALID_TO, IS_CURRENT FROM CUSTOMER_HISTORY WHERE ID=1")) {
				assertTrue(rs.next());
				assertEquals("old", rs.getString(1));
				assertNull(rs.getTimestamp(2));
				assertTrue(rs.getBoolean(3));
				assertFalse(rs.next());
			}
			try (var statement = connection.createStatement();
					var rs = statement.executeQuery("SELECT COUNT(*) FROM CALLER_WORK")) {
				rs.next();
				assertEquals(0, rs.getInt(1));
			}
		}
	}

	@Test
	void rejectsDuplicateSourceKeysBeforeChangingHistory() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:hsql_set_scd2_source_duplicate", "SA", "")) {
			createHistory(connection, "(1, 'old', TIMESTAMP '2026-01-01 00:00:00', NULL, TRUE)");
			final var definition = definition();

			final var error = assertThrows(java.sql.SQLException.class,
					() -> SetBasedMigrationSnapshotResolver.resolve(connection).execute(connection, table(), definition,
							Instant.parse("2026-09-16T00:00:00Z"), List.of(row(1, "new-a"), row(1, "new-b")), 100));

			assertTrue(error.getMessage().contains("source snapshot contains duplicate"));
			assertCurrentRows(connection, 1, "old");
		}
	}

	@Test
	void rejectsDuplicateCurrentTargetKeysBeforeChangingHistory() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:hsql_set_scd2_target_duplicate", "SA", "")) {
			createHistory(connection, "(1, 'old-a', TIMESTAMP '2026-01-01 00:00:00', NULL, TRUE), "
					+ "(1, 'old-b', TIMESTAMP '2026-02-01 00:00:00', NULL, TRUE)");
			final var definition = definition();

			final var error = assertThrows(java.sql.SQLException.class,
					() -> SetBasedMigrationSnapshotResolver.resolve(connection).execute(connection, table(), definition,
							Instant.parse("2026-09-16T00:00:00Z"), List.of(row(1, "new")), 100));

			assertTrue(error.getMessage().contains("current target snapshot contains duplicate"));
			try (var statement = connection.createStatement();
					var rs = statement.executeQuery("SELECT COUNT(*) FROM CUSTOMER_HISTORY WHERE VALID_TO IS NULL")) {
				rs.next();
				assertEquals(2, rs.getInt(1));
			}
		}
	}

	@Test
	void rejectsIncompleteOrNullSourceKeysBeforeChangingHistory() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:hsql_set_scd2_invalid_source", "SA", "")) {
			createHistory(connection, "(1, 'old', TIMESTAMP '2026-01-01 00:00:00', NULL, TRUE)");
			final var definition = definition();
			final Map<String, Object> missingTracked = new LinkedHashMap<>();
			missingTracked.put("ID", 1);
			final Map<String, Object> nullKey = row(1, "new");
			nullKey.put("ID", null);

			final var missing = assertThrows(IllegalArgumentException.class,
					() -> SetBasedMigrationSnapshotResolver.resolve(connection).execute(connection, table(), definition,
							Instant.parse("2026-09-16T00:00:00Z"), List.of(missingTracked), 100));
			assertTrue(missing.getMessage().contains("missing column: NAME"));
			final var nullable = assertThrows(IllegalArgumentException.class,
					() -> SetBasedMigrationSnapshotResolver.resolve(connection).execute(connection, table(), definition,
							Instant.parse("2026-09-16T00:00:00Z"), List.of(nullKey), 100));
			assertTrue(nullable.getMessage().contains("key must not contain null: ID"));
			assertCurrentRows(connection, 1, "old");
		}
	}

	private static MigrationSnapshotDefinition definition() {
		return new MigrationSnapshotDefinition("customer", "CUSTOMER_HISTORY", List.of("ID"), List.of("NAME"),
				"VALID_FROM", "VALID_TO", "IS_CURRENT", true);
	}

	private static void createHistory(final java.sql.Connection connection, final String values) throws Exception {
		try (var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE CUSTOMER_HISTORY (ID INTEGER NOT NULL, NAME VARCHAR(20), "
					+ "VALID_FROM TIMESTAMP NOT NULL, VALID_TO TIMESTAMP, IS_CURRENT BOOLEAN NOT NULL)");
			statement.execute("INSERT INTO CUSTOMER_HISTORY VALUES " + values);
		}
	}

	private static void assertCurrentRows(final java.sql.Connection connection, final int expected, final String name)
			throws Exception {
		try (var statement = connection.createStatement();
				var rs = statement.executeQuery("SELECT NAME FROM CUSTOMER_HISTORY WHERE VALID_TO IS NULL")) {
			int count = 0;
			while (rs.next()) {
				assertEquals(name, rs.getString(1));
				count++;
			}
			assertEquals(expected, count);
		}
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

	private static Map<String, Object> row(final int id, final String name) {
		final Map<String, Object> row = new LinkedHashMap<>();
		row.put("ID", id);
		row.put("NAME", name);
		return row;
	}
}
