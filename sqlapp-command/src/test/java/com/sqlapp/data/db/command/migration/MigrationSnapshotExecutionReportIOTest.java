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
	@TempDir
	Path directory;

	@Test
	void atomicallyRoundTripsAValidatedReport() {
		final var report = report(MigrationSnapshotExecutionReport.CURRENT_FORMAT_VERSION);
		final Path file = directory.resolve("nested/snapshot.json");

		new MigrationSnapshotExecutionReportIO().write(file, report);

		assertEquals(report, new MigrationSnapshotExecutionReportIO().read(file));
		assertEquals(report, new MigrationSnapshotExecutionReportIO().read(file, report.configurationFingerprint()));
		assertThrows(CommandException.class,
				() -> new MigrationSnapshotExecutionReportIO().read(file, "sha256:" + "f".repeat(64)));
	}

	@Test
	void rejectsUnsupportedAndCorruptReports() throws Exception {
		final var io = new MigrationSnapshotExecutionReportIO();
		assertThrows(CommandException.class, () -> io.write(directory.resolve("unsupported.json"), report(2)));
		final MigrationSnapshotExecutionReport valid = report(MigrationSnapshotExecutionReport.CURRENT_FORMAT_VERSION);
		final var invalidFingerprint = new MigrationSnapshotExecutionReport(valid.formatVersion(), valid.generatedAt(),
				valid.startedAt(), valid.snapshotId(), "invalid", valid.approvalGeneratedAt(),
				valid.approvalArtifactFingerprint(), valid.sourceTable(), valid.targetTable(), valid.keyColumns(),
				valid.trackedColumns(), valid.expireMissingRows(), valid.effectiveAt(), valid.fetchSize(),
				valid.batchSize(), valid.approvalValidFor(), valid.lease(), valid.leaseAcquisitionId(),
				valid.databaseProductName(), valid.databaseProductVersion(), valid.executorClassName(),
				valid.callerTransactionAtomicity(), valid.expiredRows(), valid.insertedRows(), valid.unchangedRows());
		assertThrows(CommandException.class,
				() -> io.write(directory.resolve("invalid-fingerprint.json"), invalidFingerprint));
		final Path corrupt = directory.resolve("corrupt.json");
		Files.writeString(corrupt, "{\"formatVersion\":1}");
		assertThrows(CommandException.class, () -> io.read(corrupt));
	}

	@Test
	void rejectsInvalidExecutionAndApprovalChronology() {
		final MigrationSnapshotExecutionReport valid = report(MigrationSnapshotExecutionReport.CURRENT_FORMAT_VERSION);
		final var completionBeforeStart = copy(valid, Instant.parse("2026-09-16T00:58:00Z"), valid.startedAt(), null,
				null, null);
		assertThrows(CommandException.class, () -> new MigrationSnapshotExecutionReportIO()
				.write(directory.resolve("before-start.json"), completionBeforeStart));
		final var approvalAfterStart = copy(valid, valid.generatedAt(), valid.startedAt(),
				Instant.parse("2026-09-16T01:00:00Z"), "sha256:" + "1".repeat(64), java.time.Duration.ofHours(1));
		assertThrows(CommandException.class, () -> new MigrationSnapshotExecutionReportIO()
				.write(directory.resolve("approval-after.json"), approvalAfterStart));
		final var expiredAtStart = copy(valid, valid.generatedAt(), valid.startedAt(),
				Instant.parse("2026-09-16T00:00:00Z"), "sha256:" + "1".repeat(64), java.time.Duration.ofMinutes(30));
		assertThrows(CommandException.class, () -> new MigrationSnapshotExecutionReportIO()
				.write(directory.resolve("expired.json"), expiredAtStart));
		final var expiresExactlyAtStart = copy(valid, valid.generatedAt(), valid.startedAt(),
				Instant.parse("2026-09-16T00:00:00Z"), "sha256:" + "1".repeat(64), java.time.Duration.ofMinutes(59));
		assertThrows(CommandException.class, () -> new MigrationSnapshotExecutionReportIO()
				.write(directory.resolve("expired-at-boundary.json"), expiresExactlyAtStart));
	}

	@Test
	void requiresLeaseAndAcquisitionEvidenceTogether() {
		final MigrationSnapshotExecutionReport valid = report(MigrationSnapshotExecutionReport.CURRENT_FORMAT_VERSION);
		final var missingAcquisition = new MigrationSnapshotExecutionReport(valid.formatVersion(), valid.generatedAt(),
				valid.startedAt(), valid.snapshotId(), valid.configurationFingerprint(), valid.approvalGeneratedAt(),
				valid.approvalArtifactFingerprint(), valid.sourceTable(), valid.targetTable(), valid.keyColumns(),
				valid.trackedColumns(), valid.expireMissingRows(), valid.effectiveAt(), valid.fetchSize(),
				valid.batchSize(), valid.approvalValidFor(), valid.lease(), null, valid.databaseProductName(),
				valid.databaseProductVersion(), valid.executorClassName(), valid.callerTransactionAtomicity(),
				valid.expiredRows(), valid.insertedRows(), valid.unchangedRows());
		assertThrows(CommandException.class, () -> new MigrationSnapshotExecutionReportIO()
				.write(directory.resolve("missing-acquisition.json"), missingAcquisition));
	}

	private static MigrationSnapshotExecutionReport report(final int version) {
		return new MigrationSnapshotExecutionReport(version, Instant.parse("2026-09-16T01:00:00Z"),
				Instant.parse("2026-09-16T00:59:00Z"), "customer", "sha256:" + "0".repeat(64), null, null,
				"PUBLIC.CUSTOMER", "PUBLIC.CUSTOMER_HISTORY", List.of("ID"), List.of("NAME"), true,
				Instant.parse("2026-09-16T00:00:00Z"), 1000, 500, null,
				new MigrationSnapshotLeaseEvidence(com.sqlapp.jdbc.bulk.BulkMigrationJobLeaseMode.FILE, "worker-1",
						java.time.Duration.ofMinutes(5), null, "C:\\leases"),
				"acquisition-1", "HSQL Database Engine", "2.7", "example.Executor", true, 2, 2, 1);
	}

	private static MigrationSnapshotExecutionReport copy(final MigrationSnapshotExecutionReport value,
			final Instant generatedAt, final Instant startedAt, final Instant approvalGeneratedAt,
			final String approvalArtifactFingerprint, final java.time.Duration approvalValidFor) {
		return new MigrationSnapshotExecutionReport(value.formatVersion(), generatedAt, startedAt, value.snapshotId(),
				value.configurationFingerprint(), approvalGeneratedAt, approvalArtifactFingerprint, value.sourceTable(),
				value.targetTable(), value.keyColumns(), value.trackedColumns(), value.expireMissingRows(),
				value.effectiveAt(), value.fetchSize(), value.batchSize(), approvalValidFor, value.lease(),
				value.leaseAcquisitionId(), value.databaseProductName(), value.databaseProductVersion(),
				value.executorClassName(), value.callerTransactionAtomicity(), value.expiredRows(),
				value.insertedRows(), value.unchangedRows());
	}
}
