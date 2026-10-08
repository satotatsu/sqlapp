/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlanner;

class BulkMigrationJobExecutionReportIOTest {
	@TempDir
	Path directory;

	@Test
	void roundTripsBoundedExecutionCountsAndRejectsInconsistentTotals() {
		final var task = new BulkMigrationJobExecutionReport.Task("ACCESS_ITEMS", 10, 15, 2, false);
		final var report = new BulkMigrationJobExecutionReport(BulkMigrationJobExecutionReport.CURRENT_FORMAT_VERSION,
				Instant.now(), "access-job", "plan", 15, 0, List.of(task), null);
		final Path file = directory.resolve("reports/execution.json");
		final var io = new BulkMigrationJobExecutionReportIO();
		final var snapshot = io.writeSnapshot(file, report, 10_000L);

		assertEquals(report, snapshot.report());
		assertEquals(report, io.read(file, 10_000L));
		assertThrows(CommandException.class, () -> io.read(file, 1L));
		assertThrows(CommandException.class, () -> io.writeSnapshot(file,
				new BulkMigrationJobExecutionReport(1, Instant.now(), "access-job", "plan", 14, 0, List.of(task), null),
				null));
	}

	@Test
	void writesASeparateFailureResultWithoutPretendingExecutionCompleted() {
		final var plan = BulkMigrationJobPlanner.plan("access-job", List.of());
		final var io = new BulkMigrationJobFailureReportIO();
		final var report = io.fromFailure(plan, new IllegalStateException("source read failed"), null);
		final Path file = directory.resolve("reports/execution-failure.json");
		final var snapshot = io.writeSnapshot(file, report, 10_000L);

		assertEquals("FAILED", snapshot.report().status());
		assertEquals(List.of(), snapshot.report().completedTasks());
		assertEquals(report, io.read(file, 10_000L));
	}
}
