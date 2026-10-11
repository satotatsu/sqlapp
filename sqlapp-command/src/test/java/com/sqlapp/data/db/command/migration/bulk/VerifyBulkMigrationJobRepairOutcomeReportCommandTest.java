/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.MessageDigests;

class VerifyBulkMigrationJobRepairOutcomeReportCommandTest {
	@TempDir
	Path directory;

	@Test
	void revalidatesSavedOutcomeAndRejectsTamperingAndFutureEvidence() throws Exception {
		final var approval = approval();
		final Path approvalFile = directory.resolve("approval.json");
		new BulkMigrationJobRepairPlanReportIO().write(approvalFile, approval);
		final var provenance = new BulkMigrationArtifactProvenance(sha('c'), null, null);
		final var completed = new BulkMigrationJobRepairExecutionReport.Task("parent", 1, 1, 1, 1, List.of(),
				List.of());
		final var failure = new BulkMigrationJobRepairFailureReport(
				BulkMigrationJobRepairFailureReport.CURRENT_FORMAT_VERSION, Instant.now(), "migration",
				approval.planFingerprint(), fingerprint(approvalFile), null, "EXECUTION", "child",
				"java.sql.SQLException", "failed", List.of(completed), provenance);
		final Path failureFile = directory.resolve("failure.json");
		new BulkMigrationJobRepairFailureReportIO().write(failureFile, failure);
		final Path outcomeFile = directory.resolve("outcome.json");
		final var generate = new VerifyBulkMigrationJobRepairOutcomeCommand();
		generate.setApprovedRepairPlanFile(approvalFile.toFile());
		generate.setRepairFailureReportFile(failureFile.toFile());
		generate.setOutcomeReportFile(outcomeFile.toFile());
		generate.run();
		assertEquals(fingerprint(outcomeFile), generate.getOutcomeReportFingerprint());

		final var verify = command(outcomeFile, approvalFile, failureFile);
		verify.setExpectedOutcomeReportFingerprint(fingerprint(outcomeFile));
		verify.setExpectedStatus("EXECUTION_FAILED");
		verify.setExpectedMigrationPlanFingerprint("migration");
		verify.setExpectedRepairPlanFingerprint(approval.planFingerprint());
		verify.setExpectedConfigurationFingerprint(provenance.configurationFingerprint());
		verify.setMaxEvidenceAgeSeconds(60L);
		verify.run();
		assertEquals(generate.getOutcomeReport(), verify.getReport());

		final var saved = generate.getOutcomeReport();
		new BulkMigrationJobRepairOutcomeReportIO().write(outcomeFile, copy(saved, saved.generatedAt(), sha('f')));
		verify.setExpectedOutcomeReportFingerprint(null);
		assertThrows(CommandException.class, verify::run);
		assertNull(verify.getReport());

		new BulkMigrationJobRepairOutcomeReportIO().write(outcomeFile,
				copy(saved, Instant.now().plusSeconds(60), saved.repairFailureReportFingerprint()));
		assertThrows(CommandException.class, verify::run);
		assertNull(verify.getReport());
	}

	private static VerifyBulkMigrationJobRepairOutcomeReportCommand command(final Path outcome, final Path approval,
			final Path failure) {
		final var command = new VerifyBulkMigrationJobRepairOutcomeReportCommand();
		command.setOutcomeReportFile(outcome.toFile());
		command.setApprovedRepairPlanFile(approval.toFile());
		command.setRepairFailureReportFile(failure.toFile());
		return command;
	}

	private static BulkMigrationJobRepairOutcomeReport copy(final BulkMigrationJobRepairOutcomeReport source,
			final Instant generatedAt, final String failureFingerprint) {
		return new BulkMigrationJobRepairOutcomeReport(source.formatVersion(), generatedAt, source.status(),
				source.migrationPlanFingerprint(), source.repairPlanFingerprint(),
				source.approvedRepairPlanFileFingerprint(), source.repairExecutionReportFingerprint(),
				failureFingerprint, source.postRepairVerificationReportFingerprint(), source.failurePhase(),
				source.failedTaskId(), source.provenance());
	}

	private static BulkMigrationJobRepairPlanReport approval() throws Exception {
		final var tasks = List.of(new BulkMigrationJobRepairPlanReport.Task("parent", child("parent-plan")),
				new BulkMigrationJobRepairPlanReport.Task("child", child("child-plan")));
		final MessageDigest digest = MessageDigest.getInstance("SHA-256");
		for (final var task : tasks) {
			for (final Object value : List.of(task.taskId(), task.repairPlan().planFingerprint())) {
				final byte[] bytes = String.valueOf(value).getBytes(StandardCharsets.UTF_8);
				digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
				digest.update(bytes);
			}
		}
		return new BulkMigrationJobRepairPlanReport(1, Instant.now(), HexFormat.of().formatHex(digest.digest()), 2, 2,
				true, tasks);
	}

	private static BulkMigrationRepairPlanReport child(final String fingerprint) {
		return new BulkMigrationRepairPlanReport(1, Instant.now(), fingerprint,
				new BulkMigrationRepairPlanReport.Relation(null, null, "SOURCE_ROWS"),
				new BulkMigrationRepairPlanReport.Relation(null, null, "TARGET_ROWS"), false, null, null, "HSQLDB", "2",
				"executor", true, false, "stage", 1, 1, 100, true, List.of("ID"), List.of("ID"), List.of("ID"),
				List.of(), List.of(new BulkMigrationRepairPlanReport.Chunk(0, 1, 0, "a", "b", null, null, null, null)));
	}

	private static String fingerprint(final Path file) {
		return "sha256:" + MessageDigests.SHA256.checksumAsString(file.toFile());
	}

	private static String sha(final char value) {
		return "sha256:" + String.valueOf(value).repeat(64);
	}
}
