/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.sqlapp.AbstractDbTest;

class JdbcBulkMigrationMaintenanceStateStoreTest extends AbstractDbTest {
	@Test
	void readOnlyStoreDoesNotCreateOrModifyTheMaintenanceTable() throws Exception {
		testDb(connection -> {
			final var reader = JdbcBulkMigrationMaintenanceStateStore.readOnly(connection,
					"SQLAPP_MAINTENANCE_READ_ONLY");
			assertTrue(reader.load("plan-v1").isEmpty());
			assertFalse(tableExists(connection, "SQLAPP_MAINTENANCE_READ_ONLY"));
			final var state = new BulkMigrationMaintenanceState("plan-v1",
					BulkMigrationMaintenanceStatus.PREPARED, Instant.EPOCH, null);
			assertThrows(UnsupportedOperationException.class, () -> reader.save(state));
			assertThrows(UnsupportedOperationException.class,
					() -> reader.delete("plan-v1"));

			new JdbcBulkMigrationMaintenanceStateStore(connection,
					"SQLAPP_MAINTENANCE_READ_ONLY").save(state);
			assertEquals(state, reader.load("plan-v1").orElseThrow());
		});
	}

	@Test
	void readOnlyStoreScopesAndEscapesMetadataLookup() throws Exception {
		testDb(connection -> {
			execute(connection, "CREATE SCHEMA OTHER_MAINTENANCE AUTHORIZATION DBA");
			execute(connection, "CREATE TABLE OTHER_MAINTENANCE.SQLAPP_MAINTENANCE_SCOPED ("
					+ "PLAN_FINGERPRINT VARCHAR(255) PRIMARY KEY)");
			assertTrue(JdbcBulkMigrationMaintenanceStateStore.readOnly(connection,
					"SQLAPP_MAINTENANCE_SCOPED").load("plan-v1").isEmpty());

			execute(connection, "CREATE SCHEMA \"MAINTENANCE_%\" AUTHORIZATION DBA");
			connection.setSchema("MAINTENANCE_%");
			final var state = new BulkMigrationMaintenanceState("plan-v1",
					BulkMigrationMaintenanceStatus.PREPARED, Instant.EPOCH, null);
			new JdbcBulkMigrationMaintenanceStateStore(connection,
					"SQLAPP_MAINTENANCE_WILDCARD").save(state);
			assertEquals(state, JdbcBulkMigrationMaintenanceStateStore.readOnly(connection,
					"SQLAPP_MAINTENANCE_WILDCARD").load("plan-v1").orElseThrow());
		});
	}

	@Test
	void readOnlyStoreRejectsInvalidStructureAndCorruptData() throws Exception {
		testDb(connection -> {
			execute(connection, "CREATE TABLE SQLAPP_MAINTENANCE_INVALID ("
					+ "PLAN_FINGERPRINT VARCHAR(255) PRIMARY KEY)");
			assertTrue(assertThrows(java.sql.SQLException.class,
					() -> JdbcBulkMigrationMaintenanceStateStore.readOnly(connection,
							"SQLAPP_MAINTENANCE_INVALID").load("plan-v1"))
					.getMessage().contains("missing required columns"));

			new JdbcBulkMigrationMaintenanceStateStore(connection,
					"SQLAPP_MAINTENANCE_CORRUPT");
			execute(connection, "INSERT INTO SQLAPP_MAINTENANCE_CORRUPT VALUES ("
					+ "'plan-v1', 'UNKNOWN', 'not-an-instant', NULL)");
			final var failure = assertThrows(java.sql.SQLException.class,
					() -> JdbcBulkMigrationMaintenanceStateStore.readOnly(connection,
							"SQLAPP_MAINTENANCE_CORRUPT").load("plan-v1"));
			assertTrue(failure.getMessage().contains("Invalid migration maintenance data"));
			assertTrue(failure.getCause() instanceof RuntimeException);
		});
	}

	@Test
	void readOnlyStoreRejectsAmbiguousTablesAndCompositePrimaryKeys()
			throws Exception {
		testDb(connection -> {
			execute(connection, "CREATE TABLE SQLAPP_MAINTENANCE_AMBIGUOUS ("
					+ "PLAN_FINGERPRINT VARCHAR(255) PRIMARY KEY, "
					+ "STATUS_NAME VARCHAR(32), UPDATED_AT VARCHAR(40), "
					+ "FAILURE_MESSAGE VARCHAR(255))");
			execute(connection, "CREATE TABLE \"sqlapp_maintenance_ambiguous\" ("
					+ "PLAN_FINGERPRINT VARCHAR(255) PRIMARY KEY, "
					+ "STATUS_NAME VARCHAR(32), UPDATED_AT VARCHAR(40), "
					+ "FAILURE_MESSAGE VARCHAR(255))");
			assertTrue(assertThrows(java.sql.SQLException.class,
					() -> JdbcBulkMigrationMaintenanceStateStore.readOnly(connection,
							"SQLAPP_MAINTENANCE_AMBIGUOUS").load("plan-v1"))
					.getMessage().contains("Ambiguous"));

			execute(connection, "CREATE TABLE SQLAPP_MAINTENANCE_COMPOSITE ("
					+ "PLAN_FINGERPRINT VARCHAR(255), STATUS_NAME VARCHAR(32), "
					+ "UPDATED_AT VARCHAR(40), FAILURE_MESSAGE VARCHAR(255), "
					+ "PRIMARY KEY (PLAN_FINGERPRINT, STATUS_NAME))");
			assertTrue(assertThrows(java.sql.SQLException.class,
					() -> JdbcBulkMigrationMaintenanceStateStore.readOnly(connection,
							"SQLAPP_MAINTENANCE_COMPOSITE").load("plan-v1"))
					.getMessage().contains("PLAN_FINGERPRINT alone"));
		});
	}

	@Test
	void createsSavesReplacesLoadsAndDeletesStateThroughSqlFactories()
			throws Exception {
		testDb(connection -> {
			final var store = new JdbcBulkMigrationMaintenanceStateStore(connection,
					"SQLAPP_MAINTENANCE_STATE_TEST");
			final String fingerprint = "plan-v1";
			final var prepared = new BulkMigrationMaintenanceState(fingerprint,
					BulkMigrationMaintenanceStatus.PREPARED,
					Instant.parse("2026-08-31T02:00:00Z"), null);
			store.save(prepared);
			assertEquals(prepared, store.load(fingerprint).orElseThrow());

			final var failed = new BulkMigrationMaintenanceState(fingerprint,
					BulkMigrationMaintenanceStatus.RESTORE_FAILED,
					Instant.parse("2026-08-31T02:01:00Z"), "restore failed");
			store.save(failed);
			assertEquals(failed, store.load(fingerprint).orElseThrow());

			store.delete(fingerprint);
			assertFalse(store.load(fingerprint).isPresent());
		});
	}

	private static boolean tableExists(final java.sql.Connection connection,
			final String name) throws java.sql.SQLException {
		try (var tables = connection.getMetaData().getTables(
				connection.getCatalog(), null, "%", new String[] { "TABLE" })) {
			while (tables.next()) {
				if (name.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
					return true;
				}
			}
		}
		return false;
	}
}
