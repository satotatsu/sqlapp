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

class MigrationSnapshotFailureReportIOTest {
	@TempDir Path directory;

	@Test
	void atomicallyRoundTripsAndValidatesFailureReports() throws Exception {
		final var report = report("failure");
		final Path file = directory.resolve("nested/failure.json");
		final var io = new MigrationSnapshotFailureReportIO();
		io.write(file, report);
		assertEquals(report, io.read(file));

		assertThrows(CommandException.class, () -> io.write(directory.resolve("long.json"),
				report("x".repeat(MigrationSnapshotFailureReportIO.FAILURE_MESSAGE_MAX_LENGTH + 1))));
		Files.writeString(directory.resolve("corrupt.json"), "{\"formatVersion\":1}");
		assertThrows(CommandException.class, () -> io.read(directory.resolve("corrupt.json")));
	}

	@Test
	void verifiesTheExactApprovalArtifact() {
		final Path approvalFile = directory.resolve("approval.json");
		final var approval = new MigrationSnapshotApprovalReport(
				MigrationSnapshotApprovalReport.CURRENT_FORMAT_VERSION, Instant.parse("2026-09-16T00:30:00Z"),
				"sha256:" + "0".repeat(64), "customer", "PUBLIC.CUSTOMER", "PUBLIC.CUSTOMER_HISTORY",
				List.of("ID"), List.of("NAME"), true, Instant.parse("2026-09-16T00:00:00Z"), 100, 100, null);
		final var approvalIo = new MigrationSnapshotApprovalReportIO();
		approvalIo.write(approvalFile, approval);
		final String artifactFingerprint = approvalIo.readArtifact(approvalFile).artifactFingerprint();
		final var failure = new MigrationSnapshotFailureReport(MigrationSnapshotFailureReport.CURRENT_FORMAT_VERSION,
				Instant.parse("2026-09-16T01:00:00Z"), Instant.parse("2026-09-16T00:59:00Z"),
				MigrationSnapshotFailurePhase.DATABASE_EXECUTION, "customer", approval.configurationFingerprint(),
				approval.generatedAt(), artifactFingerprint, approval.sourceTable(), approval.targetTable(),
				approval.effectiveAt(), "java.sql.SQLException", "failure");
		final Path failureFile = directory.resolve("failure.json");
		final var failureIo = new MigrationSnapshotFailureReportIO();
		failureIo.write(failureFile, failure);

		final var command = new VerifyMigrationSnapshotFailureReportCommand();
		command.setReportFile(failureFile.toFile());
		command.setApprovalFile(approvalFile.toFile());
		command.run();
		assertEquals(failure, command.getReport());
		assertEquals(approval, command.getApproval());
	}

	private static MigrationSnapshotFailureReport report(final String message) {
		return new MigrationSnapshotFailureReport(MigrationSnapshotFailureReport.CURRENT_FORMAT_VERSION,
				Instant.parse("2026-09-16T01:00:00Z"), Instant.parse("2026-09-16T00:59:00Z"),
				MigrationSnapshotFailurePhase.DATABASE_EXECUTION, "customer", "sha256:" + "0".repeat(64), null, null,
				"PUBLIC.CUSTOMER", "PUBLIC.CUSTOMER_HISTORY", Instant.parse("2026-09-16T00:00:00Z"),
				"java.sql.SQLException", message);
	}
}
