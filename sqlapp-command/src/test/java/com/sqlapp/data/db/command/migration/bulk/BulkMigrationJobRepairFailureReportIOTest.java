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
import com.sqlapp.util.MessageDigests;

class BulkMigrationJobRepairFailureReportIOTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void roundTripsFailurePhaseAndCompletedPrefix() {
		final var completed = new BulkMigrationJobRepairExecutionReport.Task("parent", 1, 1, 2, 2, List.of(),
				List.of());
		final var report = new BulkMigrationJobRepairFailureReport(
				BulkMigrationJobRepairFailureReport.CURRENT_FORMAT_VERSION, Instant.now(), "migration", "repair",
				"sha256:" + "a".repeat(64), null, "EXECUTION", "child", "java.sql.SQLException", "write failed",
				List.of(completed), null);
		final Path file = temporaryDirectory.resolve("repair-failure.json");
		final var io = new BulkMigrationJobRepairFailureReportIO();

		io.write(file, report);

		assertEquals(report, io.read(file));
		final var snapshot = io.readSnapshot(file, null);
		assertEquals(report, snapshot.report());
		assertEquals("sha256:" + MessageDigests.SHA256.checksumAsString(file.toFile()), snapshot.fingerprint());
	}

	@Test
	void rejectsUnknownFailurePhase() {
		final var report = new BulkMigrationJobRepairFailureReport(
				BulkMigrationJobRepairFailureReport.CURRENT_FORMAT_VERSION, Instant.now(), "migration", "repair",
				"sha256:" + "b".repeat(64), null, "UNKNOWN", "items", "failure", "message", List.of(), null);

		assertThrows(CommandException.class, () -> new BulkMigrationJobRepairFailureReportIO()
				.write(temporaryDirectory.resolve("invalid.json"), report));
	}

	@Test
	void rejectsInvalidCompletedTaskMetricsAndPreflightResults() {
		final var invalidMetrics = new BulkMigrationJobRepairExecutionReport.Task("items", 1, 2, 1, 1, List.of(),
				List.of());
		final var execution = report("EXECUTION", List.of(invalidMetrics));
		final var completedPreflight = report("PREFLIGHT",
				List.of(new BulkMigrationJobRepairExecutionReport.Task("items", 1, 1, 1, 1, List.of(), List.of())));

		assertThrows(CommandException.class, () -> new BulkMigrationJobRepairFailureReportIO()
				.write(temporaryDirectory.resolve("invalid-metrics.json"), execution));
		assertThrows(CommandException.class, () -> new BulkMigrationJobRepairFailureReportIO()
				.write(temporaryDirectory.resolve("invalid-preflight.json"), completedPreflight));
	}

	@Test
	void rejectsAChunkClassifiedAsBothExtraAndMissing() {
		final var overlap = new BulkMigrationJobRepairExecutionReport.Task("items", 1, 0, 0, 0, List.of(3L),
				List.of(3L));

		assertThrows(CommandException.class, () -> new BulkMigrationJobRepairFailureReportIO()
				.write(temporaryDirectory.resolve("overlap.json"), report("EXECUTION", List.of(overlap))));
	}

	private static BulkMigrationJobRepairFailureReport report(final String phase,
			final List<BulkMigrationJobRepairExecutionReport.Task> tasks) {
		return new BulkMigrationJobRepairFailureReport(BulkMigrationJobRepairFailureReport.CURRENT_FORMAT_VERSION,
				Instant.now(), "migration", "repair", "sha256:" + "c".repeat(64), null, phase, "items", "failure",
				"message", tasks, null);
	}
}
