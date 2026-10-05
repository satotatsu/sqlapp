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

class VerifyBulkMigrationJobRepairFailureEvidenceCommandTest {
	@TempDir
	Path directory;

	@Test
	void verifiesApprovalFingerprintCompletedPrefixAndExpectedIdentities() throws Exception {
		final var approval = approval();
		final Path approvalFile = directory.resolve("approval.json");
		new BulkMigrationJobRepairPlanReportIO().write(approvalFile, approval);
		final var completed = new BulkMigrationJobRepairExecutionReport.Task("parent", 1, 1, 1, 1,
				List.of(), List.of());
		final var provenance = new BulkMigrationArtifactProvenance("sha256:" + "b".repeat(64), null, null);
		final var failure = new BulkMigrationJobRepairFailureReport(BulkMigrationJobRepairFailureReport.CURRENT_FORMAT_VERSION, Instant.now(), "migration", approval.planFingerprint(),
				fingerprint(approvalFile), null, "EXECUTION", "child", "java.sql.SQLException", "write failed",
				List.of(completed), provenance);
		final Path failureFile = directory.resolve("failure.json");
		new BulkMigrationJobRepairFailureReportIO().write(failureFile, failure);
		final var command = command(approvalFile, failureFile);
		command.setExpectedRepairFailureReportFingerprint(fingerprint(failureFile));
		command.setExpectedMigrationPlanFingerprint("migration");
		command.setExpectedRepairPlanFingerprint(approval.planFingerprint());
		command.setExpectedConfigurationFingerprint(provenance.configurationFingerprint());
		command.setMaxEvidenceAgeSeconds(60L);

		command.run();

		assertEquals(failure, command.getReport());
		command.setExpectedRepairFailureReportFingerprint("sha256:" + "0".repeat(64));
		assertThrows(CommandException.class, command::run);
		assertNull(command.getReport());
	}

	@Test
	void rejectsCompletedTasksThatAreNotAnApprovedPrefix() throws Exception {
		final var approval = approval();
		final Path approvalFile = directory.resolve("approval.json");
		new BulkMigrationJobRepairPlanReportIO().write(approvalFile, approval);
		final var completed = new BulkMigrationJobRepairExecutionReport.Task("child", 1, 1, 1, 1,
				List.of(), List.of());
		final var failure = new BulkMigrationJobRepairFailureReport(BulkMigrationJobRepairFailureReport.CURRENT_FORMAT_VERSION, Instant.now(), "migration", approval.planFingerprint(),
				fingerprint(approvalFile), null, "EXECUTION", "parent", "failure", "message", List.of(completed), null);
		final Path failureFile = directory.resolve("failure.json");
		new BulkMigrationJobRepairFailureReportIO().write(failureFile, failure);

		final var rejection = assertThrows(CommandException.class,
				command(approvalFile, failureFile)::run);

		assertEquals("Repair failure report does not match approvedRepairPlanFile.", rejection.getMessage());
	}

	@Test
	void verifiesPostRepairMismatchAgainstItsVerificationArtifact() throws Exception {
		final var approval = approval();
		final Path approvalFile = directory.resolve("approval.json");
		new BulkMigrationJobRepairPlanReportIO().write(approvalFile, approval);
		final var parent = new BulkMigrationVerificationReport.Task("parent", List.of("ID"), null, null,
				true, 1, 1, 0, List.of());
		final var mismatch = new BulkMigrationVerificationReport.Chunk(0, 1, 1, "expected", "actual",
				null, null, null, null);
		final var child = new BulkMigrationVerificationReport.Task("child", List.of("ID"), null, null,
				false, 1, 1, 1, List.of(mismatch));
		final var provenance = new BulkMigrationArtifactProvenance("sha256:" + "c".repeat(64), null, null);
		final Path verificationFile = directory.resolve("post-verification.json");
		new BulkMigrationVerificationReportIO().write(verificationFile,
				new BulkMigrationVerificationReport(BulkMigrationVerificationReport.CURRENT_FORMAT_VERSION,
						Instant.now(), "migration", "READ_COMMITTED", false, 2, 2, 1,
						List.of(parent, child), provenance));
		final var completed = List.of(
				new BulkMigrationJobRepairExecutionReport.Task("parent", 1, 1, 1, 1, List.of(), List.of()),
				new BulkMigrationJobRepairExecutionReport.Task("child", 1, 1, 1, 1, List.of(), List.of()));
		final var failure = new BulkMigrationJobRepairFailureReport(
				BulkMigrationJobRepairFailureReport.CURRENT_FORMAT_VERSION, Instant.now(), "migration",
				approval.planFingerprint(), fingerprint(approvalFile), fingerprint(verificationFile),
				"POST_VERIFICATION", "child", CommandException.class.getName(), "mismatch", completed, provenance);
		final Path failureFile = directory.resolve("failure.json");
		new BulkMigrationJobRepairFailureReportIO().write(failureFile, failure);
		final var command = command(approvalFile, failureFile);
		command.setPostRepairVerificationReportFile(verificationFile.toFile());

		command.run();

		assertEquals(failure, command.getReport());
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
		return new BulkMigrationJobRepairPlanReport(1, Instant.now(), HexFormat.of().formatHex(digest.digest()),
				2, 2, true, tasks);
	}

	private static BulkMigrationRepairPlanReport child(final String fingerprint) {
		return new BulkMigrationRepairPlanReport(1, Instant.now(), fingerprint,
				new BulkMigrationRepairPlanReport.Relation(null, null, "SOURCE_ROWS"),
				new BulkMigrationRepairPlanReport.Relation(null, null, "TARGET_ROWS"), false, null, null, "HSQLDB",
				"2", "executor", true, false, "stage", 1, 1, 100, true, List.of("ID"), List.of("ID"),
				List.of("ID"), List.of(), List.of(new BulkMigrationRepairPlanReport.Chunk(0, 1, 0, "a", "b",
						null, null, null, null)));
	}

	private static String fingerprint(final Path file) {
		return "sha256:" + MessageDigests.SHA256.checksumAsString(file.toFile());
	}
}
