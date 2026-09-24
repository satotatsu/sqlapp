/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VerifyMigrationExecutionReportCommandTest {
	@TempDir
	Path directory;

	@Test
	void verifiesIntegrityExternalFingerprintAndSuccessPolicy() {
		final long now = System.currentTimeMillis();
		final String planFingerprint = "sha256:" + "1".repeat(64);
		final String connectionFingerprint = "sha256:" + "2".repeat(64);
		final var report = new MigrationExecutionReport(MigrationExecutionReport.CURRENT_FORMAT_VERSION, now,
				now + 1, true, true,
				new MigrationPlan.DatabaseIdentity("HSQL Database Engine", "2.7.4", connectionFingerprint),
				planFingerprint, List.of(1L), List.of(1L), null);
		final Path file = directory.resolve("execution.json");
		new MigrationExecutionReportIO().write(file, report);
		final var command = new VerifyMigrationExecutionReportCommand();
		command.setReportFile(file.toFile());
		command.setExpectedReportFingerprint(report.reportFingerprint());
		command.setExpectedPlanFingerprint(planFingerprint);
		command.setExpectedDatabaseConnectionFingerprint(connectionFingerprint);
		command.setRequireSuccessful(true);
		command.setRequireAllSelectedCommitted(true);
		command.run();
		assertEquals(report, command.getReport());
		final var mismatch = new VerifyMigrationExecutionReportCommand();
		mismatch.setReportFile(file.toFile());
		mismatch.setExpectedReportFingerprint("sha256:" + "0".repeat(64));
		assertThrows(RuntimeException.class, mismatch::run);
		final var wrongPlan = new VerifyMigrationExecutionReportCommand();
		wrongPlan.setReportFile(file.toFile());
		wrongPlan.setExpectedPlanFingerprint("sha256:" + "0".repeat(64));
		assertThrows(RuntimeException.class, wrongPlan::run);
		final var wrongDatabase = new VerifyMigrationExecutionReportCommand();
		wrongDatabase.setReportFile(file.toFile());
		wrongDatabase.setExpectedDatabaseConnectionFingerprint("sha256:" + "0".repeat(64));
		assertThrows(RuntimeException.class, wrongDatabase::run);
	}
}
