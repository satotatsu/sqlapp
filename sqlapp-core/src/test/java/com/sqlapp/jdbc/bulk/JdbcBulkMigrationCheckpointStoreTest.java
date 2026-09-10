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
			final var missing = JdbcBulkMigrationCheckpointStore.readOnly(connection,
					"SQLAPP_BMC_READ_ONLY");
			assertTrue(missing.load("migration-1").isEmpty());
			assertFalse(tableExists(connection, "SQLAPP_BMC_READ_ONLY"));
			assertThrows(UnsupportedOperationException.class,
					() -> missing.save(checkpoint(1, false, "token-1")));

			final var writable = new JdbcBulkMigrationCheckpointStore(connection,
					"SQLAPP_BMC_READ_ONLY");
			writable.save(checkpoint(1, true, "token-1"));
			final var existing = JdbcBulkMigrationCheckpointStore.readOnly(connection,
					"SQLAPP_BMC_READ_ONLY");
			assertEquals(1, existing.load("migration-1").orElseThrow()
					.getProcessedRows());
		});
	}

	@Test
	void readOnlyStoreIgnoresCheckpointTablesInOtherSchemas() throws Exception {
		testDb(connection -> {
			execute(connection, "CREATE SCHEMA OTHER_CHECKPOINT AUTHORIZATION DBA");
			execute(connection, "CREATE TABLE OTHER_CHECKPOINT.SQLAPP_BMC_SCOPED ("
					+ "MIGRATION_ID VARCHAR(255) PRIMARY KEY)");

			final var store = JdbcBulkMigrationCheckpointStore.readOnly(connection,
					"SQLAPP_BMC_SCOPED");
			assertTrue(store.load("migration-1").isEmpty());
		});
	}

	@Test
	void readOnlyStoreFindsTableInSchemaContainingMetadataWildcards() throws Exception {
		testDb(connection -> {
			execute(connection, "CREATE SCHEMA \"CHECKPOINT_%\" AUTHORIZATION DBA");
			connection.setSchema("CHECKPOINT_%");
			final var writable = new JdbcBulkMigrationCheckpointStore(connection,
					"SQLAPP_BMC_WILDCARD");
			writable.save(checkpoint(2, false, "token-2"));

			final var reader = JdbcBulkMigrationCheckpointStore.readOnly(connection,
					"SQLAPP_BMC_WILDCARD");
			assertEquals(2, reader.load("migration-1").orElseThrow()
					.getProcessedRows());
		});
	}

	@Test
	void readOnlyStoreRejectsAmbiguousCaseSensitiveTableMatches() throws Exception {
		testDb(connection -> {
			execute(connection, "CREATE TABLE SQLAPP_BMC_AMBIGUOUS (ID INTEGER)");
			execute(connection, "CREATE TABLE \"sqlapp_bmc_ambiguous\" (ID INTEGER)");

			final var store = JdbcBulkMigrationCheckpointStore.readOnly(connection,
					"SQLAPP_BMC_AMBIGUOUS");
			final var failure = assertThrows(java.sql.SQLException.class,
					() -> store.load("migration-1"));
			assertTrue(failure.getMessage().contains("Ambiguous"));
		});
	}

	@Test
	void readOnlyStoreRejectsInvalidTableStructure() throws Exception {
		testDb(connection -> {
			execute(connection, "CREATE TABLE SQLAPP_BMC_INVALID ("
					+ "MIGRATION_ID VARCHAR(255) PRIMARY KEY)");
			final var missingColumns = JdbcBulkMigrationCheckpointStore.readOnly(connection,
					"SQLAPP_BMC_INVALID");
			assertTrue(assertThrows(java.sql.SQLException.class,
					() -> missingColumns.load("migration-1")).getMessage()
					.contains("missing required columns"));

			execute(connection, "CREATE TABLE SQLAPP_BMC_COMPOSITE ("
					+ "MIGRATION_ID VARCHAR(255) NOT NULL, "
					+ "SOURCE_FINGERPRINT VARCHAR(255), TARGET_FINGERPRINT VARCHAR(255), "
					+ "PROCESSED_ROWS DECIMAL(19,0), COMPLETED_CHUNKS DECIMAL(19,0), "
					+ "CHUNK_SIZE INTEGER, LAST_CHUNK_HASH VARCHAR(64), "
					+ "RESUME_TOKEN VARCHAR(4000), COMPLETE_FLAG CHAR(1), "
					+ "PRIMARY KEY (MIGRATION_ID, SOURCE_FINGERPRINT))");
			final var compositeKey = JdbcBulkMigrationCheckpointStore.readOnly(connection,
					"SQLAPP_BMC_COMPOSITE");
			assertTrue(assertThrows(java.sql.SQLException.class,
					() -> compositeKey.load("migration-1")).getMessage()
					.contains("MIGRATION_ID alone"));
		});
	}

	@Test
	void writableStoreRejectsInvalidBaseColumnsAndPrimaryKey() throws Exception {
		testDb(connection -> {
			execute(connection, "CREATE TABLE SQLAPP_BMC_WRITE_MISSING ("
					+ "MIGRATION_ID VARCHAR(255) PRIMARY KEY)");
			final var missing = assertThrows(java.sql.SQLException.class,
					() -> new JdbcBulkMigrationCheckpointStore(connection,
							"SQLAPP_BMC_WRITE_MISSING"));
			assertTrue(missing.getMessage().contains("missing required columns"));

			execute(connection, "CREATE TABLE SQLAPP_BMC_WRITE_WRONG_PK ("
					+ "MIGRATION_ID VARCHAR(255) NOT NULL, "
					+ "SOURCE_FINGERPRINT VARCHAR(255), TARGET_FINGERPRINT VARCHAR(255), "
					+ "PROCESSED_ROWS DECIMAL(19,0), COMPLETED_CHUNKS DECIMAL(19,0), "
					+ "LAST_CHUNK_HASH VARCHAR(64), COMPLETE_FLAG CHAR(1), "
					+ "PRIMARY KEY (MIGRATION_ID, SOURCE_FINGERPRINT))");
			final var wrongKey = assertThrows(java.sql.SQLException.class,
					() -> new JdbcBulkMigrationCheckpointStore(connection,
							"SQLAPP_BMC_WRITE_WRONG_PK"));
			assertTrue(wrongKey.getMessage().contains("MIGRATION_ID alone"));
		});
	}

	@Test
	void readOnlyStoreWrapsCorruptCheckpointData() throws Exception {
		testDb(connection -> {
			new JdbcBulkMigrationCheckpointStore(connection, "SQLAPP_BMC_CORRUPT");
			execute(connection, "INSERT INTO SQLAPP_BMC_CORRUPT VALUES ("
					+ "'migration-1', 'source-v1', 'target-v1', 2, 0, 1, "
					+ "'hash-2', 'token-2', '0')");
			final var reader = JdbcBulkMigrationCheckpointStore.readOnly(connection,
					"SQLAPP_BMC_CORRUPT");
			final var failure = assertThrows(java.sql.SQLException.class,
					() -> reader.load("migration-1"));
			assertTrue(failure.getMessage().contains("Invalid migration checkpoint data"));
			assertTrue(failure.getCause() instanceof IllegalArgumentException);
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
