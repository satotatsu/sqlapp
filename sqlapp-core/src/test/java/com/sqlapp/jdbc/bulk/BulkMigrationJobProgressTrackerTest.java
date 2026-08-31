/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

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

	@Test
	void validatesTotalConfigurationAgainstThePlanBeforeExecution() {
		final var plan = BulkMigrationJobPlanner.plan(java.util.List.of(
				task("customers", "customers-copy"), task("orders", "orders-copy")));
		final Map<String, Long> totals = new LinkedHashMap<>();
		totals.put("orders-copy", 20L);
		totals.put("customers-copy", 10L);
		final var tracker = new BulkMigrationJobProgressTracker(plan, totals, null);
		assertEquals(java.util.List.of("customers-copy", "orders-copy"),
				tracker.getTotalRowsByMigration().keySet().stream().toList());

		assertThrows(IllegalArgumentException.class, () ->
				new BulkMigrationJobProgressTracker(plan,
						Map.of("customers-copy", 10L), null));
		assertThrows(IllegalArgumentException.class, () ->
				new BulkMigrationJobProgressTracker(plan,
						Map.of("customers-copy", 10L, "orders-copy", 20L,
								"other", 1L), null));
	}

	private static BulkMigrationJobTask task(final String taskId,
			final String migrationId) {
		final Table table = new Table(taskId.toUpperCase());
		table.getColumns().add(new Column("ID"));
		table.setPrimaryKey("PK_" + table.getName(), table.getColumns().get("ID"));
		return BulkMigrationJobTask.builder().taskId(taskId).sourceTable(table)
				.options(ChunkedBulkMigrationOption.builder().migrationId(migrationId)
						.sourceFingerprint("source").targetFingerprint("target").build())
				.build();
	}
}
