/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BulkMigrationIncrementalStrategyTest {

	@Test
	void mapsLegacyModesWithoutChangingTheirBehavior() {
		final var append = ChunkedBulkMigrationOption.builder().migrationId("append").mode(BulkMigrationMode.INSERT)
				.resume(false).build();
		final var merge = ChunkedBulkMigrationOption.builder().migrationId("merge").mode(BulkMigrationMode.UPSERT)
				.resume(false).build();

		assertEquals(BulkMigrationIncrementalStrategy.APPEND, append.getIncrementalStrategy());
		assertEquals(BulkMigrationIncrementalStrategy.MERGE, merge.getIncrementalStrategy());
	}

	@Test
	void requiresStrategyAndLegacyModeToAgreeAndRejectsUnimplementedStrategies() {
		assertThrows(IllegalArgumentException.class,
				() -> ChunkedBulkMigrationOption.builder().migrationId("mismatch").mode(BulkMigrationMode.INSERT)
						.incrementalStrategy(BulkMigrationIncrementalStrategy.MERGE).resume(false).build());
		assertThrows(IllegalArgumentException.class,
				() -> ChunkedBulkMigrationOption.builder().migrationId("microbatch").mode(BulkMigrationMode.UPSERT)
						.incrementalStrategy(BulkMigrationIncrementalStrategy.MICROBATCH).resume(false).build());
	}
}
