/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class BulkMigrationProgressTrackerTest {
	@Test
	void calculatesDurableProgressRateAndEtaAcrossResumeBoundary() {
		final AtomicLong nanos = new AtomicLong();
		final List<BulkMigrationProgressSnapshot> snapshots = new ArrayList<>();
		final var tracker = new BulkMigrationProgressTracker(100L, snapshots::add,
				nanos::get);
		tracker.onChunkStarted(progress(40, 50));
		nanos.set(Duration.ofSeconds(2).toNanos());
		tracker.onChunkCompleted(progress(40, 50));

		final var first = tracker.getLatest();
		assertEquals(50, first.processedRows());
		assertEquals(5d, first.rowsPerSecond());
		assertEquals(0.5d, first.completionRatio());
		assertEquals(Duration.ofSeconds(10), first.estimatedRemaining());

		nanos.set(Duration.ofSeconds(6).toNanos());
		tracker.onChunkCompleted(progress(50, 70));
		final var second = tracker.getLatest();
		assertEquals(5d, second.rowsPerSecond());
		assertEquals(Duration.ofSeconds(6), second.estimatedRemaining());
		assertEquals(2, snapshots.size());
	}

	@Test
	void leavesRatioAndEtaUnknownWithoutATotal() {
		final AtomicLong nanos = new AtomicLong();
		final var tracker = new BulkMigrationProgressTracker(null, null, nanos::get);
		tracker.onChunkStarted(progress(0, 10));
		nanos.set(Duration.ofSeconds(1).toNanos());
		tracker.onChunkCompleted(progress(0, 10));

		assertEquals(10d, tracker.getLatest().rowsPerSecond());
		assertNull(tracker.getLatest().completionRatio());
		assertNull(tracker.getLatest().estimatedRemaining());
	}

	@Test
	void rejectsProgressBeyondTheDeclaredTotal() {
		final var tracker = new BulkMigrationProgressTracker(5L, null, () -> 1L);
		assertThrows(IllegalArgumentException.class,
				() -> tracker.onChunkCompleted(progress(0, 10)));
	}

	private static ChunkedBulkMigrationProgress progress(final long before,
			final long after) {
		return new ChunkedBulkMigrationProgress("migration", 0,
				(int) (after - before), before, after);
	}
}
