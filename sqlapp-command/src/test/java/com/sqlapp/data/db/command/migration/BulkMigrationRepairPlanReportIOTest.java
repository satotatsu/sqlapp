/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.exceptions.CommandException;

class BulkMigrationRepairPlanReportIOTest {
	@TempDir
	Path directory;

	@Test
	void writesReadsAndChecksTheApprovedFingerprint() {
		final var io = new BulkMigrationRepairPlanReportIO();
		final Path file = directory.resolve("repair-plan.json");
		final var report = report(1, "plan-fingerprint", 1);

		io.write(file, report);

		assertEquals(report, io.read(file));
		assertEquals(report, io.read(file, "plan-fingerprint"));
		assertThrows(CommandException.class, () -> io.read(file, "another-plan"));
	}

	@Test
	void rejectsUnsupportedVersionsAndInconsistentReplayCounts() {
		final var io = new BulkMigrationRepairPlanReportIO();
		assertThrows(CommandException.class,
				() -> io.write(directory.resolve("version.json"),
						report(99, "plan-fingerprint", 1)));
		assertThrows(CommandException.class,
				() -> io.write(directory.resolve("rows.json"),
						report(1, "plan-fingerprint", 2)));
	}

	@Test
	void rejectsMissingFilesAndEmptyExpectedFingerprints() {
		final var io = new BulkMigrationRepairPlanReportIO();
		final Path file = directory.resolve("missing.json");
		assertThrows(CommandException.class, () -> io.read(file));
		assertThrows(IllegalArgumentException.class, () -> io.read(file, " "));
	}

	private static BulkMigrationRepairPlanReport report(final int formatVersion,
			final String fingerprint, final long estimatedRows) {
		return new BulkMigrationRepairPlanReport(formatVersion, Instant.parse(
				"2026-09-03T00:00:00Z"), fingerprint,
				new BulkMigrationRepairPlanReport.Relation(null, "SOURCE_SCHEMA", "SOURCE_ROWS"),
				new BulkMigrationRepairPlanReport.Relation(null, "TARGET_SCHEMA", "TARGET_ROWS"),
				true, "expected-keyset", "actual-keyset", "SQLite", "3.50",
				"com.example.SqliteBulkUpsertExecutor", true, false, "repair_stage",
				1, estimatedRows, 100, true, List.of("ID", "TXT"), List.of("ID"),
				List.of("ID", "TXT"), List.of("TXT"),
				List.of(new BulkMigrationRepairPlanReport.Chunk(0, 1, 1,
						"expected-hash", "actual-hash", "first", "last", "first", "last")));
	}
}
