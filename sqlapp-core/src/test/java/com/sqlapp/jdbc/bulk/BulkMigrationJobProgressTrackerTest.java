/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class BulkMigrationJobProgressTrackerTest {
	@Test
	void tracksEachMigrationWithAnIndependentResumeBaseline() {
		final var emitted = new ArrayList<BulkMigrationProgressSnapshot>();
		final Map<String, Long> totals = new LinkedHashMap<>();
		totals.put("customers", 100L);
		totals.put("orders", 250L);
		final var tracker = new BulkMigrationJobProgressTracker(totals, emitted::add);

		tracker.onChunkStarted(new ChunkedBulkMigrationProgress("customers", 2, 10, 20, 30));
		tracker.onChunkCompleted(new ChunkedBulkMigrationProgress("customers", 2, 10, 20, 30));
		tracker.onChunkStarted(new ChunkedBulkMigrationProgress("orders", 5, 25, 100, 125));
		tracker.onChunkCompleted(new ChunkedBulkMigrationProgress("orders", 5, 25, 100, 125));

		assertEquals(2, emitted.size());
		assertEquals(30, tracker.getSnapshots().get("customers").processedRows());
		assertEquals(125, tracker.getSnapshots().get("orders").processedRows());
		assertEquals("orders", tracker.getLatest().migrationId());
		assertEquals(0.5, tracker.getLatest().completionRatio());
		assertThrows(UnsupportedOperationException.class,
				() -> tracker.getSnapshots().clear());
	}

	@Test
	void supportsUnknownTotalsButRejectsUnknownMigrationEvents() {
		final Map<String, Long> totals = new LinkedHashMap<>();
		totals.put("known", null);
		final var tracker = new BulkMigrationJobProgressTracker(totals, null);
		final var known = new ChunkedBulkMigrationProgress("known", 0, 1, 0, 1);
		tracker.onChunkStarted(known);
		tracker.onChunkCompleted(known);
		assertTrue(tracker.getLatest().totalRows() == null);

		assertThrows(IllegalArgumentException.class, () -> tracker.onChunkStarted(
				new ChunkedBulkMigrationProgress("other", 0, 1, 0, 1)));
	}
}
