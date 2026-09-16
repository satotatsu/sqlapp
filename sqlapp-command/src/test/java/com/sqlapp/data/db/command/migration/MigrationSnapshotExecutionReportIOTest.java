/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.exceptions.CommandException;

class MigrationSnapshotExecutionReportIOTest {
	@TempDir Path directory;

	@Test
	void atomicallyRoundTripsAValidatedReport() {
		final var report = report(MigrationSnapshotExecutionReport.CURRENT_FORMAT_VERSION);
		final Path file = directory.resolve("nested/snapshot.json");

		new MigrationSnapshotExecutionReportIO().write(file, report);

		assertEquals(report, new MigrationSnapshotExecutionReportIO().read(file));
		assertEquals(report,
				new MigrationSnapshotExecutionReportIO().read(file, report.configurationFingerprint()));
		assertThrows(CommandException.class,
				() -> new MigrationSnapshotExecutionReportIO().read(file, "sha256:" + "f".repeat(64)));
	}

	@Test
	void rejectsUnsupportedAndCorruptReports() throws Exception {
		final var io = new MigrationSnapshotExecutionReportIO();
		assertThrows(CommandException.class, () -> io.write(directory.resolve("unsupported.json"), report(2)));
		final MigrationSnapshotExecutionReport valid = report(MigrationSnapshotExecutionReport.CURRENT_FORMAT_VERSION);
		final var invalidFingerprint = new MigrationSnapshotExecutionReport(valid.formatVersion(), valid.generatedAt(),
				valid.snapshotId(), "invalid", valid.sourceTable(), valid.targetTable(), valid.keyColumns(),
				valid.trackedColumns(), valid.expireMissingRows(), valid.effectiveAt(), valid.fetchSize(),
				valid.batchSize(), valid.databaseProductName(), valid.databaseProductVersion(),
				valid.executorClassName(), valid.callerTransactionAtomicity(), valid.expiredRows(),
				valid.insertedRows(), valid.unchangedRows());
		assertThrows(CommandException.class,
				() -> io.write(directory.resolve("invalid-fingerprint.json"), invalidFingerprint));
		final Path corrupt = directory.resolve("corrupt.json");
		Files.writeString(corrupt, "{\"formatVersion\":1}");
		assertThrows(CommandException.class, () -> io.read(corrupt));
	}

	private static MigrationSnapshotExecutionReport report(final int version) {
		return new MigrationSnapshotExecutionReport(version, Instant.parse("2026-09-16T01:00:00Z"), "customer",
				"sha256:" + "0".repeat(64), "PUBLIC.CUSTOMER", "PUBLIC.CUSTOMER_HISTORY", List.of("ID"), List.of("NAME"), true,
				Instant.parse("2026-09-16T00:00:00Z"), 1000, 500, "HSQL Database Engine", "2.7",
				"example.Executor", true, 2, 2, 1);
	}
}
