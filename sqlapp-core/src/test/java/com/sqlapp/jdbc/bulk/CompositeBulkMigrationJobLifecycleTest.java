/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class CompositeBulkMigrationJobLifecycleTest {
	@Test
	void combinesPlansAndRunsRestoreInReverseWithoutSkippingFailures()
			throws Exception {
		final List<String> events = new ArrayList<>();
		final var first = component("first", events, false);
		final var second = component("second", events, true);
		final var lifecycle = CompositeBulkMigrationJobLifecycle.of(first, second);
		final var plan = BulkMigrationJobPlanner.plan(List.of(), lifecycle);

		assertEquals(List.of("first", "second"), plan.getOperations().stream()
				.map(BulkMigrationJobOperation::id).toList());
		lifecycle.before(null, plan);
		lifecycle.after(null, plan, new BulkMigrationJobResult(List.of()));
		final SQLException failure = assertThrows(SQLException.class,
				() -> lifecycle.restore(null, plan, new SQLException("migration")));

		assertEquals(List.of("before-first", "before-second", "after-first",
				"after-second", "restore-second", "restore-first"), events);
		assertEquals("restore-second-failed", failure.getMessage());
	}

	@Test
	void rejectsDuplicateOperationIdsWhilePlanning() {
		final var lifecycle = CompositeBulkMigrationJobLifecycle.of(
				component("same", new ArrayList<>(), false),
				component("same", new ArrayList<>(), false));
		assertThrows(IllegalArgumentException.class,
				() -> BulkMigrationJobPlanner.plan(List.of(), lifecycle));
	}

	private static BulkMigrationJobLifecycle component(final String id,
			final List<String> events, final boolean failRestore) {
		return new BulkMigrationJobLifecycle() {
			@Override
			public String getConfigurationFingerprint() {
				return id;
			}

			@Override
			public List<BulkMigrationJobOperation> plan(List<BulkMigrationJobTask> tasks) {
				return List.of(new BulkMigrationJobOperation(id,
						BulkMigrationJobOperationPhase.BEFORE, id, false));
			}

			@Override
			public void before(Connection connection, BulkMigrationJobPlan plan) {
				events.add("before-" + id);
			}

			@Override
			public void after(Connection connection, BulkMigrationJobPlan plan,
					BulkMigrationJobResult result) {
				events.add("after-" + id);
			}

			@Override
			public void restore(Connection connection, BulkMigrationJobPlan plan,
					Throwable failure) throws SQLException {
				events.add("restore-" + id);
				if (failRestore) {
					throw new SQLException("restore-" + id + "-failed");
				}
			}
		};
	}
}
