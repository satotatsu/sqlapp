/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
			assertEquals("token-2", loaded.getResumeToken());

			store.save(checkpoint(5, true, "token-5"));
			loaded = store.load("migration-1").orElseThrow();
			assertEquals(5, loaded.getProcessedRows());
			assertTrue(loaded.isComplete());

			store.delete("migration-1");
			assertTrue(store.load("migration-1").isEmpty());
		});
	}

	private static BulkMigrationCheckpoint checkpoint(final long processedRows,
			final boolean complete, final String resumeToken) {
		return BulkMigrationCheckpoint.builder().migrationId("migration-1")
				.sourceFingerprint("source-v1").targetFingerprint("target-v1")
				.processedRows(processedRows).completedChunks(processedRows)
				.lastChunkHash("hash-" + processedRows).resumeToken(resumeToken)
				.complete(complete).build();
	}
}
