/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.MessageDigests;

class BulkMigrationJobRepairOutcomeReportIOTest {
	@TempDir
	Path directory;

	@Test
	void roundTripsSuccessfulOutcome() {
		final var report = new BulkMigrationJobRepairOutcomeReport(1, Instant.now(), "SUCCEEDED", "migration",
				"repair", sha('a'), sha('b'), null, sha('c'), null, null,
				new BulkMigrationArtifactProvenance(sha('d'), null, null));
		final Path file = directory.resolve("outcome.json");

		new BulkMigrationJobRepairOutcomeReportIO().write(file, report);

		final var io = new BulkMigrationJobRepairOutcomeReportIO();
		assertEquals(report, io.read(file));
		final var snapshot = io.readSnapshot(file, null);
		assertEquals(report, snapshot.report());
		assertEquals("sha256:" + MessageDigests.SHA256.checksumAsString(file.toFile()), snapshot.fingerprint());
	}

	@Test
	void rejectsContradictoryStatusEvidence() {
		final var successWithFailure = new BulkMigrationJobRepairOutcomeReport(1, Instant.now(), "SUCCEEDED",
				"migration", "repair", sha('a'), sha('b'), sha('c'), sha('d'), "EXECUTION", "task", null);
		final var executionFailureWithVerification = new BulkMigrationJobRepairOutcomeReport(1, Instant.now(),
				"EXECUTION_FAILED", "migration", "repair", sha('a'), null, sha('b'), sha('c'), "EXECUTION",
				"task", null);
		final var verificationFailureWithoutVerification = new BulkMigrationJobRepairOutcomeReport(1, Instant.now(),
				"VERIFICATION_FAILED", "migration", "repair", sha('a'), sha('b'), sha('c'), null,
				"POST_VERIFICATION", "task", null);

		assertThrows(CommandException.class, () -> BulkMigrationJobRepairOutcomeReportIO.validate(successWithFailure));
		assertThrows(CommandException.class,
				() -> BulkMigrationJobRepairOutcomeReportIO.validate(executionFailureWithVerification));
		assertThrows(CommandException.class,
				() -> BulkMigrationJobRepairOutcomeReportIO.validate(verificationFailureWithoutVerification));
	}

	private static String sha(final char value) {
		return "sha256:" + String.valueOf(value).repeat(64);
	}
}
