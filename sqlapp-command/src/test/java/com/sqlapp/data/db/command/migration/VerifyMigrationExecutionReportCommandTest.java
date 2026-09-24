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
	void freshnessIsOptionalAndRejectsFutureCompletionTimes() {
		final long future = System.currentTimeMillis() + 120_000;
		final var report = new MigrationExecutionReport(MigrationExecutionReport.CURRENT_FORMAT_VERSION,
				future, future, true, true, null, null, List.of(), List.of(), null);
		final Path file = directory.resolve("future.json");
		new MigrationExecutionReportIO().write(file, report);
		final var command = new VerifyMigrationExecutionReportCommand();
		command.setReportFile(file.toFile());
		command.run();
		command.setMaxReportAgeSeconds(60L);
		final var exception = assertThrows(com.sqlapp.exceptions.CommandException.class, command::run);
		org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("future"));
	}

	@Test
	void rejectsInvalidAgeBeforeReadingReport() {
		final var command = new VerifyMigrationExecutionReportCommand();
		command.setReportFile(directory.resolve("missing.json").toFile());
		for (final long invalid : new long[] { 0, -1, Long.MAX_VALUE }) {
			command.setMaxReportAgeSeconds(invalid);
			final var exception = assertThrows(com.sqlapp.exceptions.CommandException.class, command::run);
			org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("maxReportAgeSeconds"));
		}
	}

	@Test
	void verifiesIntegrityExternalFingerprintAndSuccessPolicy() {
		final long now = System.currentTimeMillis();
		final String planFingerprint = "sha256:" + "1".repeat(64);
		final String connectionFingerprint = "sha256:" + "2".repeat(64);
		final var report = new MigrationExecutionReport(MigrationExecutionReport.CURRENT_FORMAT_VERSION, now,
				now, true, true,
				new MigrationPlan.DatabaseIdentity("HSQL Database Engine", "2.7.4", connectionFingerprint),
				planFingerprint, List.of(1L), List.of(1L), null);
		final Path file = directory.resolve("execution.json");
		new MigrationExecutionReportIO().write(file, report);
		final var command = new VerifyMigrationExecutionReportCommand();
		command.setReportFile(file.toFile());
		command.setExpectedReportFingerprint(report.reportFingerprint());
		command.setExpectedPlanFingerprint(planFingerprint);
		command.setExpectedDatabaseConnectionFingerprint(connectionFingerprint);
		command.setMaxReportAgeSeconds(60L);
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
		final var expired = new MigrationExecutionReport(MigrationExecutionReport.CURRENT_FORMAT_VERSION,
				now - 120_000, now - 120_000, true, true,
				new MigrationPlan.DatabaseIdentity("HSQL Database Engine", "2.7.4", connectionFingerprint),
				planFingerprint, List.of(1L), List.of(1L), null);
		final Path expiredFile = directory.resolve("expired-execution.json");
		new MigrationExecutionReportIO().write(expiredFile, expired);
		final var expiredCommand = new VerifyMigrationExecutionReportCommand();
		expiredCommand.setReportFile(expiredFile.toFile());
		expiredCommand.setMaxReportAgeSeconds(60L);
		assertThrows(RuntimeException.class, expiredCommand::run);
	}
}
