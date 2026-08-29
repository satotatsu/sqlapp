/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BulkMigrationCheckpointTest {
	@Test
	void validatesPortableCheckpointStorageLimits() {
		final var valid = BulkMigrationCheckpoint.builder().migrationId("migration")
				.sourceFingerprint("s".repeat(255)).targetFingerprint("t".repeat(255))
				.lastChunkHash("h".repeat(64)).resumeToken("r".repeat(4_000)).build();

		assertSame(valid, valid.validate());
		assertThrows(IllegalArgumentException.class, () -> BulkMigrationCheckpoint.builder()
				.migrationId("m".repeat(256)).build().validate());
		assertThrows(IllegalArgumentException.class, () -> valid.toBuilder()
				.sourceFingerprint("s".repeat(256)).build().validate());
		assertThrows(IllegalArgumentException.class, () -> valid.toBuilder()
				.lastChunkHash("h".repeat(65)).build().validate());
		assertThrows(IllegalArgumentException.class, () -> valid.toBuilder()
				.resumeToken("r".repeat(4_001)).build().validate());
	}

	@Test
	void rejectsNegativeProgressBeforePersistence() {
		assertThrows(IllegalArgumentException.class, () -> BulkMigrationCheckpoint.builder()
				.migrationId("migration").processedRows(-1).build().validate());
		assertThrows(IllegalArgumentException.class, () -> BulkMigrationCheckpoint.builder()
				.migrationId("migration").completedChunks(-1).build().validate());
	}
}
