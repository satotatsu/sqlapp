/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class CompositeBulkMigrationJobListenerTest {
	@Test
	void dispatchesEveryJobAndTaskEventInRegistrationOrder() {
		final List<String> events = new ArrayList<>();
		final var composite = CompositeBulkMigrationJobListener.of(
				listener("first", events), listener("second", events));
		final var progress = new ChunkedBulkMigrationProgress("migration", 0, 1, 0, 1);
		final var chunkResult = new ChunkedBulkMigrationResult(0, 1, 1, false);
		final var jobResult = new BulkMigrationJobResult("fingerprint", List.of());

		composite.onJobStarted("fingerprint", 1);
		composite.onTaskStarted("task", 0, 1);
		composite.onTaskCompleted("task", chunkResult, 0, 1);
		composite.onTaskFailed("task", new SQLException("failed"), 0, 1);
		composite.onTaskPaused("task", progress, 0, 1);
		composite.onJobCompleted(jobResult);
		composite.onJobFailed("fingerprint", new SQLException("failed"));
		composite.onJobPaused("fingerprint", "task", progress);

		assertEquals(List.of("job-start-first", "job-start-second",
				"task-start-first", "task-start-second",
				"task-complete-first", "task-complete-second",
				"task-fail-first", "task-fail-second",
				"task-pause-first", "task-pause-second",
				"job-complete-first", "job-complete-second",
				"job-fail-first", "job-fail-second",
				"job-pause-first", "job-pause-second"), events);
		assertThrows(UnsupportedOperationException.class,
				() -> composite.getListeners().clear());
	}

	private static BulkMigrationJobListener listener(final String name,
			final List<String> events) {
		return new BulkMigrationJobListener() {
			@Override public void onJobStarted(String fingerprint, int count) {
				events.add("job-start-" + name);
			}
			@Override public void onJobCompleted(BulkMigrationJobResult result) {
				events.add("job-complete-" + name);
			}
			@Override public void onJobFailed(String fingerprint, Throwable cause) {
				events.add("job-fail-" + name);
			}
			@Override public void onJobPaused(String fingerprint, String taskId,
					ChunkedBulkMigrationProgress progress) {
				events.add("job-pause-" + name);
			}
			@Override public void onTaskStarted(String taskId, int index, int count) {
				events.add("task-start-" + name);
			}
			@Override public void onTaskCompleted(String taskId,
					ChunkedBulkMigrationResult result, int index, int count) {
				events.add("task-complete-" + name);
			}
			@Override public void onTaskFailed(String taskId, SQLException cause,
					int index, int count) {
				events.add("task-fail-" + name);
			}
			@Override public void onTaskPaused(String taskId,
					ChunkedBulkMigrationProgress progress, int index, int count) {
				events.add("task-pause-" + name);
			}
		};
	}
}
