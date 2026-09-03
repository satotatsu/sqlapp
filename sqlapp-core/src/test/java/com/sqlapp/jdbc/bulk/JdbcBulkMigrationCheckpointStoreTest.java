/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.AbstractDbTest;

class JdbcBulkMigrationCheckpointStoreTest extends AbstractDbTest {
	@Test
	void generatesAndExecutesCheckpointDmlThroughSqlFactories() throws Exception {
		testDb(connection -> {
			final var store = new JdbcBulkMigrationCheckpointStore(connection,
					"SQLAPP_BMC_FACTORY_TEST");
			store.save(checkpoint(2, false, "token-2"));
			var loaded = store.load("migration-1").orElseThrow();
			assertEquals(2, loaded.getProcessedRows());
			assertEquals(1, loaded.getChunkSize());
			assertEquals("token-2", loaded.getResumeToken());

			store.save(checkpoint(5, true, "token-5"));
			loaded = store.load("migration-1").orElseThrow();
			assertEquals(5, loaded.getProcessedRows());
			assertTrue(loaded.isComplete());

			store.delete("migration-1");
			assertTrue(store.load("migration-1").isEmpty());
		});
	}

	@Test
	void upgradesLegacyCheckpointTableThroughSchemaDifference() throws Exception {
		testDb(connection -> {
			execute(connection, "CREATE TABLE SQLAPP_BMC_LEGACY ("
					+ "MIGRATION_ID VARCHAR(255) NOT NULL PRIMARY KEY, "
					+ "SOURCE_FINGERPRINT VARCHAR(255), TARGET_FINGERPRINT VARCHAR(255), "
					+ "PROCESSED_ROWS DECIMAL(19,0) NOT NULL, "
					+ "COMPLETED_CHUNKS DECIMAL(19,0) NOT NULL, "
					+ "LAST_CHUNK_HASH VARCHAR(64), COMPLETE_FLAG CHAR(1) NOT NULL)");

			final var store = new JdbcBulkMigrationCheckpointStore(connection,
					"SQLAPP_BMC_LEGACY");
			store.save(checkpoint(3, false, "legacy-token"));

			assertEquals("legacy-token", store.load("migration-1").orElseThrow()
					.getResumeToken());
		});
	}

	@Test
	void readOnlyStoreDoesNotCreateOrModifyTheCheckpointTable() throws Exception {
		testDb(connection -> {
			final var missing = new ReadOnlyJdbcBulkMigrationCheckpointStore(connection,
					"SQLAPP_BMC_READ_ONLY");
			assertTrue(missing.load("migration-1").isEmpty());
			assertFalse(tableExists(connection, "SQLAPP_BMC_READ_ONLY"));
			assertThrows(UnsupportedOperationException.class,
					() -> missing.save(checkpoint(1, false, "token-1")));

			final var writable = new JdbcBulkMigrationCheckpointStore(connection,
					"SQLAPP_BMC_READ_ONLY");
			writable.save(checkpoint(1, true, "token-1"));
			final var existing = new ReadOnlyJdbcBulkMigrationCheckpointStore(connection,
					"SQLAPP_BMC_READ_ONLY");
			assertEquals(1, existing.load("migration-1").orElseThrow()
					.getProcessedRows());
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

	private static BulkMigrationCheckpoint checkpoint(final long processedRows,
			final boolean complete, final String resumeToken) {
		return BulkMigrationCheckpoint.builder().migrationId("migration-1")
				.sourceFingerprint("source-v1").targetFingerprint("target-v1")
				.processedRows(processedRows).completedChunks(processedRows).chunkSize(1)
				.lastChunkHash("hash-" + processedRows).resumeToken(resumeToken)
				.complete(complete).build();
	}
}
