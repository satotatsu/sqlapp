/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

class VerifyBulkMigrationJobRepairFailureEvidenceCommandTest {
	@TempDir
	Path directory;

	@Test
	void verifiesApprovalFingerprintCompletedPrefixAndExpectedIdentities() throws Exception {
		final var approval = approval();
		final Path approvalFile = directory.resolve("approval.json");
		new BulkMigrationJobRepairPlanReportIO().write(approvalFile, approval);
		final var completed = new BulkMigrationJobRepairExecutionReport.Task("parent", 1, 1, 1, 1, List.of(),
				List.of());
		final var provenance = new BulkMigrationArtifactProvenance("sha256:" + "b".repeat(64), null, null);
		final var failure = new BulkMigrationJobRepairFailureReport(
				BulkMigrationJobRepairFailureReport.CURRENT_FORMAT_VERSION, Instant.now(), "migration",
				approval.planFingerprint(), fingerprint(approvalFile), null, "EXECUTION", "child",
				"java.sql.SQLException", "write failed", List.of(completed), provenance);
		final Path failureFile = directory.resolve("repair-failure.json");
		new BulkMigrationJobRepairFailureReportIO().write(failureFile, failure);
		final var command = new VerifyBulkMigrationJobRepairFailureEvidenceCommand();
		command.setApprovedRepairPlanFile(approvalFile.toFile());
		command.setRepairReportDirectory(directory.toFile());
		command.setExpectedApprovedRepairPlanFileFingerprint(fingerprint(approvalFile));
		command.setExpectedRepairFailureReportFingerprint(fingerprint(failureFile));
		command.setExpectedMigrationPlanFingerprint("migration");
		command.setExpectedRepairPlanFingerprint(approval.planFingerprint());
		command.setExpectedConfigurationFingerprint(provenance.configurationFingerprint());
		command.setMaxEvidenceAgeSeconds(60L);
		command.setMaxEvidenceFileSizeBytes(0L);
		final var invalidSize = assertThrows(CommandException.class, command::run);
		assertEquals("maxEvidenceFileSizeBytes must be greater than zero.", invalidSize.getMessage());
		command.setMaxEvidenceFileSizeBytes(java.nio.file.Files.size(failureFile) - 1);
		final var sizeRejection = assertThrows(CommandException.class, command::run);
		assertEquals("Bulk migration job repair failure report exceeds maxEvidenceFileSizeBytes.",
				sizeRejection.getMessage());
		assertNull(command.getReport());
		command.setMaxEvidenceFileSizeBytes(java.nio.file.Files.size(failureFile));

		command.run();

		assertEquals(failure, command.getReport());
		final var outcome = new VerifyBulkMigrationJobRepairOutcomeCommand();
		outcome.setApprovedRepairPlanFile(approvalFile.toFile());
		outcome.setRepairFailureReportFile(failureFile.toFile());
		final Path conflictingExecution = directory.resolve("conflicting-execution.json");
		java.nio.file.Files.writeString(conflictingExecution, "stale");
		outcome.setRepairExecutionReportFile(conflictingExecution.toFile());
		final Path staleOutcome = directory.resolve("execution-failed-outcome.json");
		java.nio.file.Files.writeString(staleOutcome, "stale");
		outcome.setOutcomeReportFile(staleOutcome.toFile());
		assertThrows(CommandException.class, outcome::run);
		assertNull(outcome.getStatus());
		assertFalse(java.nio.file.Files.exists(staleOutcome));
		java.nio.file.Files.delete(conflictingExecution);
		final Path outcomeFile = staleOutcome;
		outcome.setOutcomeReportFile(outcomeFile.toFile());
		outcome.run();
		assertEquals(VerifyBulkMigrationJobRepairOutcomeCommand.Status.EXECUTION_FAILED, outcome.getStatus());
		assertEquals(failure, outcome.getFailureReport());
		assertNull(outcome.getExecutionReport());
		final var outcomeReport = new BulkMigrationJobRepairOutcomeReportIO().read(outcomeFile);
		assertEquals("EXECUTION_FAILED", outcomeReport.status());
		assertEquals(fingerprint(failureFile), outcomeReport.repairFailureReportFingerprint());
		assertNull(outcomeReport.repairExecutionReportFingerprint());
		outcome.setExpectedStatus("SUCCEEDED");
		final var statusRejection = assertThrows(CommandException.class, outcome::run);
		assertEquals("Repair outcome status EXECUTION_FAILED does not match expectedStatus SUCCEEDED.",
				statusRejection.getMessage());
		assertEquals(VerifyBulkMigrationJobRepairOutcomeCommand.Status.EXECUTION_FAILED, outcome.getStatus());
		final var gatedOutcomeReport = new BulkMigrationJobRepairOutcomeReportIO().read(outcomeFile);
		assertEquals("EXECUTION_FAILED", gatedOutcomeReport.status());
		assertEquals(outcomeReport.repairFailureReportFingerprint(),
				gatedOutcomeReport.repairFailureReportFingerprint());
		outcome.setExpectedStatus("UNKNOWN");
		assertThrows(CommandException.class, outcome::run);
		assertNull(outcome.getStatus());
		assertEquals(gatedOutcomeReport, new BulkMigrationJobRepairOutcomeReportIO().read(outcomeFile));
		outcome.setExpectedStatus(null);
		outcome.setOutcomeReportFile(failureFile.toFile());
		assertThrows(CommandException.class, outcome::run);
		assertNull(outcome.getStatus());
		command.setExpectedRepairFailureReportFingerprint("sha256:" + "0".repeat(64));
		assertThrows(CommandException.class, command::run);
		assertNull(command.getReport());
		command.setExpectedRepairFailureReportFingerprint(fingerprint(failureFile));
		command.setExpectedApprovedRepairPlanFileFingerprint("sha256:" + "0".repeat(64));
		final var approvalRejection = assertThrows(CommandException.class, command::run);
		assertEquals(
				"approvedRepairPlanFile fingerprint does not match " + "expectedApprovedRepairPlanFileFingerprint.",
				approvalRejection.getMessage());
		assertNull(command.getReport());
	}

	@Test
	void rejectsCompletedTasksThatAreNotAnApprovedPrefix() throws Exception {
		final var approval = approval();
		final Path approvalFile = directory.resolve("approval.json");
		new BulkMigrationJobRepairPlanReportIO().write(approvalFile, approval);
		final var completed = new BulkMigrationJobRepairExecutionReport.Task("child", 1, 1, 1, 1, List.of(), List.of());
		final var failure = new BulkMigrationJobRepairFailureReport(
				BulkMigrationJobRepairFailureReport.CURRENT_FORMAT_VERSION, Instant.now(), "migration",
				approval.planFingerprint(), fingerprint(approvalFile), null, "EXECUTION", "parent", "failure",
				"message", List.of(completed), null);
		final Path failureFile = directory.resolve("failure.json");
		new BulkMigrationJobRepairFailureReportIO().write(failureFile, failure);

		final var rejection = assertThrows(CommandException.class, command(approvalFile, failureFile)::run);

		assertEquals("Repair failure report does not match approvedRepairPlanFile.", rejection.getMessage());
	}

	@Test
	void rejectsFutureExpiredAndInvalidApprovalAgePolicies() throws Exception {
		final var base = approval();
		final Path approvalFile = directory.resolve("approval-age.json");
		final var future = new BulkMigrationJobRepairPlanReport(base.formatVersion(), Instant.now().plusSeconds(60),
				base.planFingerprint(), base.estimatedReplayRows(), base.mismatchChunks(), base.atomic(), base.tasks());
		new BulkMigrationJobRepairPlanReportIO().write(approvalFile, future);
		final var futureRejection = assertThrows(CommandException.class,
				() -> BulkMigrationJobRepairApprovalValidator.validate(approvalFile.toFile(), null, null, null));
		assertEquals("Approved repair plan generatedAt is in the future; check clock synchronization.",
				futureRejection.getMessage());

		final var stale = new BulkMigrationJobRepairPlanReport(base.formatVersion(), Instant.now().minusSeconds(120),
				base.planFingerprint(), base.estimatedReplayRows(), base.mismatchChunks(), base.atomic(), base.tasks());
		new BulkMigrationJobRepairPlanReportIO().write(approvalFile, stale);
		final var expiredRejection = assertThrows(CommandException.class,
				() -> BulkMigrationJobRepairApprovalValidator.validate(approvalFile.toFile(), null, 60L, null));
		assertEquals("Approved repair plan has expired.", expiredRejection.getMessage());
		final var invalidPolicy = assertThrows(CommandException.class,
				() -> BulkMigrationJobRepairApprovalValidator.validate(approvalFile.toFile(), null, 0L, null));
		assertEquals("maxApprovedRepairPlanAgeSeconds must be greater than zero.", invalidPolicy.getMessage());
	}

	@Test
	void verifiesPostRepairMismatchAgainstItsVerificationArtifact() throws Exception {
		final var approval = approval();
		final Path approvalFile = directory.resolve("approval.json");
		new BulkMigrationJobRepairPlanReportIO().write(approvalFile, approval);
		final var parent = new BulkMigrationVerificationReport.Task("parent", List.of("ID"), null, null, true, 1, 1, 0,
				List.of());
		final var mismatch = new BulkMigrationVerificationReport.Chunk(0, 1, 1, "expected", "actual", null, null, null,
				null);
		final var child = new BulkMigrationVerificationReport.Task("child", List.of("ID"), null, null, false, 1, 1, 1,
				List.of(mismatch));
		final var provenance = new BulkMigrationArtifactProvenance("sha256:" + "c".repeat(64), null, null);
		final Path verificationFile = directory.resolve("post-repair-verification.json");
		new BulkMigrationVerificationReportIO().write(verificationFile,
				new BulkMigrationVerificationReport(BulkMigrationVerificationReport.CURRENT_FORMAT_VERSION,
						Instant.now(), "migration", "READ_COMMITTED", false, 2, 2, 1, List.of(parent, child),
						provenance));
		final var completed = List.of(
				new BulkMigrationJobRepairExecutionReport.Task("parent", 1, 1, 1, 1, List.of(), List.of()),
				new BulkMigrationJobRepairExecutionReport.Task("child", 1, 1, 1, 1, List.of(), List.of()));
		final var failure = new BulkMigrationJobRepairFailureReport(
				BulkMigrationJobRepairFailureReport.CURRENT_FORMAT_VERSION, Instant.now(), "migration",
				approval.planFingerprint(), fingerprint(approvalFile), fingerprint(verificationFile),
				"POST_VERIFICATION", "child", CommandException.class.getName(), "mismatch", completed, provenance);
		final Path failureFile = directory.resolve("repair-failure.json");
		new BulkMigrationJobRepairFailureReportIO().write(failureFile, failure);
		final var command = new VerifyBulkMigrationJobRepairFailureEvidenceCommand();
		command.setApprovedRepairPlanFile(approvalFile.toFile());
		command.setRepairReportDirectory(directory.toFile());
		command.setExpectedPostRepairVerificationReportFingerprint(fingerprint(verificationFile));

		command.run();

		assertEquals(failure, command.getReport());
		assertEquals(new BulkMigrationVerificationReportIO().read(verificationFile),
				command.getPostRepairVerificationReport());
		final var outcome = new VerifyBulkMigrationJobRepairOutcomeCommand();
		outcome.setApprovedRepairPlanFile(approvalFile.toFile());
		outcome.setRepairFailureReportFile(failureFile.toFile());
		outcome.setPostRepairVerificationReportFile(verificationFile.toFile());
		final Path executionFile = directory.resolve("execution.json");
		final var verificationArtifact = new BulkMigrationVerificationReportIO().read(verificationFile);
		new BulkMigrationJobRepairExecutionReportIO().write(executionFile,
				new BulkMigrationJobRepairExecutionReport(BulkMigrationJobRepairExecutionReport.CURRENT_FORMAT_VERSION,
						verificationArtifact.generatedAt().minusSeconds(1), "migration", approval.planFingerprint(),
						fingerprint(approvalFile), 2, 2, 2, 2, 0, completed, provenance));
		outcome.setRepairExecutionReportFile(executionFile.toFile());
		outcome.setExpectedRepairExecutionReportFingerprint(fingerprint(executionFile));
		outcome.setExpectedPostRepairVerificationReportFingerprint(fingerprint(verificationFile));
		final Path outcomeFile = directory.resolve("verification-failed-outcome.json");
		outcome.setOutcomeReportFile(outcomeFile.toFile());
		outcome.run();
		assertEquals(VerifyBulkMigrationJobRepairOutcomeCommand.Status.VERIFICATION_FAILED, outcome.getStatus());
		assertEquals(new BulkMigrationJobRepairExecutionReportIO().read(executionFile), outcome.getExecutionReport());
		assertEquals(verificationArtifact, outcome.getPostRepairVerificationReport());
		final var outcomeReport = new BulkMigrationJobRepairOutcomeReportIO().read(outcomeFile);
		assertEquals("VERIFICATION_FAILED", outcomeReport.status());
		assertEquals(fingerprint(executionFile), outcomeReport.repairExecutionReportFingerprint());
		assertEquals(fingerprint(failureFile), outcomeReport.repairFailureReportFingerprint());
		assertEquals(fingerprint(verificationFile), outcomeReport.postRepairVerificationReportFingerprint());
		final var savedOutcome = new VerifyBulkMigrationJobRepairOutcomeReportCommand();
		savedOutcome.setOutcomeReportFile(outcomeFile.toFile());
		savedOutcome.setApprovedRepairPlanFile(approvalFile.toFile());
		savedOutcome.setRepairExecutionReportFile(executionFile.toFile());
		savedOutcome.setRepairFailureReportFile(failureFile.toFile());
		savedOutcome.setPostRepairVerificationReportFile(verificationFile.toFile());
		savedOutcome.setExpectedStatus("VERIFICATION_FAILED");
		savedOutcome.run();
		assertEquals(outcomeReport, savedOutcome.getReport());
		command.setExpectedPostRepairVerificationReportFingerprint("sha256:" + "0".repeat(64));
		assertThrows(CommandException.class, command::run);
		assertNull(command.getReport());
		assertNull(command.getPostRepairVerificationReport());
		command.setExpectedPostRepairVerificationReportFingerprint(fingerprint(verificationFile));
		java.nio.file.Files.writeString(verificationFile, System.lineSeparator(),
				java.nio.file.StandardOpenOption.APPEND);
		assertThrows(CommandException.class, command::run);
		assertNull(command.getReport());
	}

	private static VerifyBulkMigrationJobRepairFailureEvidenceCommand command(final Path approval, final Path failure) {
		final var command = new VerifyBulkMigrationJobRepairFailureEvidenceCommand();
		command.setApprovedRepairPlanFile(approval.toFile());
		command.setRepairFailureReportFile(failure.toFile());
		return command;
	}

	private static BulkMigrationJobRepairPlanReport approval() throws Exception {
		final var parent = new BulkMigrationJobRepairPlanReport.Task("parent", child("parent-plan"));
		final var child = new BulkMigrationJobRepairPlanReport.Task("child", child("child-plan"));
		final var tasks = List.of(parent, child);
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
}
