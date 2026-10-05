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

class BulkMigrationJobRepairFailureReportIOTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void roundTripsFailurePhaseAndCompletedPrefix() {
		final var completed = new BulkMigrationJobRepairExecutionReport.Task("parent", 1, 1, 2, 2, List.of(),
				List.of());
		final var report = new BulkMigrationJobRepairFailureReport(
				BulkMigrationJobRepairFailureReport.CURRENT_FORMAT_VERSION, Instant.now(), "migration", "repair",
				"sha256:" + "a".repeat(64), "EXECUTION", "child", "java.sql.SQLException", "write failed",
				List.of(completed), null);
		final Path file = temporaryDirectory.resolve("repair-failure.json");
		final var io = new BulkMigrationJobRepairFailureReportIO();

		io.write(file, report);

		assertEquals(report, io.read(file));
	}

	@Test
	void rejectsUnknownFailurePhase() {
		final var report = new BulkMigrationJobRepairFailureReport(
				BulkMigrationJobRepairFailureReport.CURRENT_FORMAT_VERSION, Instant.now(), "migration", "repair",
				"sha256:" + "b".repeat(64), "UNKNOWN", "items", "failure", "message", List.of(), null);

		assertThrows(CommandException.class,
				() -> new BulkMigrationJobRepairFailureReportIO().write(temporaryDirectory.resolve("invalid.json"), report));
	}
}
