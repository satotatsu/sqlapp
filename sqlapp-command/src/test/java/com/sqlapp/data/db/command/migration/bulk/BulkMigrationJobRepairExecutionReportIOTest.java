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
import com.sqlapp.jdbc.bulk.BulkMigrationJobRepairResult;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTaskRepairResult;
import com.sqlapp.jdbc.bulk.BulkMigrationRepairResult;
import com.sqlapp.util.MessageDigests;

class BulkMigrationJobRepairExecutionReportIOTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void roundTripsResultCountsApprovalAndProvenance() {
		final var result = new BulkMigrationJobRepairResult("repair-plan",
				List.of(new BulkMigrationJobTaskRepairResult("items",
						new BulkMigrationRepairResult(2, 1, 3, 3, List.of(1L), List.of()))));
		final var provenance = new BulkMigrationArtifactProvenance("sha256:" + "a".repeat(64), null, null,
				"sha256:" + "b".repeat(64));
		final Path file = temporaryDirectory.resolve("repair-execution.json");
		final var io = new BulkMigrationJobRepairExecutionReportIO();

		io.write(file, "migration-plan", "sha256:" + "c".repeat(64), result, provenance);
		final var report = io.read(file);
		final var snapshot = io.readSnapshot(file, null);

		assertEquals("migration-plan", report.migrationPlanFingerprint());
		assertEquals("repair-plan", report.repairPlanFingerprint());
		assertEquals(2, report.mismatchChunks());
		assertEquals(1, report.replayedChunks());
		assertEquals(3, report.replayedRows());
		assertEquals(1, report.tasksRequiringManualReconciliation());
		assertEquals(provenance, report.provenance());
		assertEquals(report, snapshot.report());
		assertEquals("sha256:" + MessageDigests.SHA256.checksumAsString(file.toFile()), snapshot.fingerprint());
	}

	@Test
	void rejectsInconsistentAggregateCounts() {
		final var task = new BulkMigrationJobRepairExecutionReport.Task("items", 1, 1, 2, 2, List.of(), List.of());
		final var report = new BulkMigrationJobRepairExecutionReport(
				BulkMigrationJobRepairExecutionReport.CURRENT_FORMAT_VERSION, Instant.now(), "migration", "repair",
				"sha256:" + "d".repeat(64), 1, 1, 99, 2, 0, List.of(task), null);

		assertThrows(CommandException.class,
				() -> new BulkMigrationJobRepairExecutionReportIO().write(
						temporaryDirectory.resolve("invalid.json"), report));
	}
}
