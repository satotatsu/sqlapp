/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.io.File;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Objects;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.exceptions.CommandException;

import lombok.Getter;
import lombok.Setter;

/**
 * Verifies saved repair approval, execution and post-repair evidence without
 * database access.
 */
@Getter
@Setter
public class VerifyBulkMigrationJobRepairEvidenceCommand extends AbstractCommand {
	private File repairReportDirectory;
	private File repairExecutionReportFile;
	private File approvedRepairPlanFile;
	private String expectedApprovedRepairPlanFileFingerprint;
	private Long maxApprovedRepairPlanAgeSeconds;
	private Long maxApprovedRepairPlanFileSizeBytes;
	private File postRepairVerificationReportFile;
	private String expectedRepairExecutionReportFingerprint;
	private String expectedPostRepairVerificationReportFingerprint;
	private String expectedMigrationPlanFingerprint;
	private String expectedRepairPlanFingerprint;
	private String expectedConfigurationFingerprint;
	private Long maxEvidenceAgeSeconds;
	private Long maxEvidenceFileSizeBytes;
	private BulkMigrationJobRepairExecutionReport report;
	private BulkMigrationVerificationReport postRepairVerificationReport;
	private String reportFingerprint;
	private String postRepairVerificationReportFingerprint;

	@Override
	protected void doRun() {
		report = null;
		reportFingerprint = null;
		postRepairVerificationReportFingerprint = null;
		postRepairVerificationReport = null;
		resolveReportFiles();
		requireFile(repairExecutionReportFile, "repairExecutionReportFile");
		requireFile(approvedRepairPlanFile, "approvedRepairPlanFile");
		final var approved = BulkMigrationJobRepairApprovalValidator.validate(approvedRepairPlanFile,
				expectedApprovedRepairPlanFileFingerprint, maxApprovedRepairPlanAgeSeconds,
				maxApprovedRepairPlanFileSizeBytes);
		final var approval = approved.report();
		requireFile(postRepairVerificationReportFile, "postRepairVerificationReportFile");
		validateSha256(expectedRepairExecutionReportFingerprint, "expectedRepairExecutionReportFingerprint");
		validateSha256(expectedPostRepairVerificationReportFingerprint,
				"expectedPostRepairVerificationReportFingerprint");
		validateSha256(expectedConfigurationFingerprint, "expectedConfigurationFingerprint");
		validateOptionalText(expectedMigrationPlanFingerprint, "expectedMigrationPlanFingerprint");
		validateOptionalText(expectedRepairPlanFingerprint, "expectedRepairPlanFingerprint");
		if (maxEvidenceAgeSeconds != null && maxEvidenceAgeSeconds <= 0) {
			throw new CommandException("maxEvidenceAgeSeconds must be greater than zero.");
		}
		final var executionSnapshot = new BulkMigrationJobRepairExecutionReportIO()
				.readSnapshot(repairExecutionReportFile.toPath(), maxEvidenceFileSizeBytes);
		final var verificationSnapshot = new BulkMigrationVerificationReportIO()
				.readSnapshot(postRepairVerificationReportFile.toPath(), maxEvidenceFileSizeBytes);
		verifyExpectedFileFingerprint(executionSnapshot.fingerprint(), verificationSnapshot.fingerprint());
		final var execution = executionSnapshot.report();
		final var verification = verificationSnapshot.report();
		if (!approved.fingerprint().equals(execution.approvedRepairPlanFileFingerprint())
				|| !approval.planFingerprint().equals(execution.repairPlanFingerprint())
				|| !approval.tasks().stream().map(BulkMigrationJobRepairPlanReport.Task::taskId).toList().equals(
						execution.tasks().stream().map(BulkMigrationJobRepairExecutionReport.Task::taskId).toList())) {
			throw new CommandException("Repair execution report does not match approvedRepairPlanFile.");
		}
		if (!verification.match() || !execution.migrationPlanFingerprint().equals(verification.planFingerprint())
				|| !execution.tasks().stream().map(BulkMigrationJobRepairExecutionReport.Task::taskId).toList().equals(
						verification.tasks().stream().map(BulkMigrationVerificationReport.Task::taskId).toList())
				|| !Objects.equals(execution.provenance(), verification.provenance())
				|| execution.completedAt().isBefore(approval.generatedAt())
				|| verification.generatedAt().isBefore(execution.completedAt())) {
			throw new CommandException("Post-repair verification report does not match successful repair execution.");
		}
		if (expectedMigrationPlanFingerprint != null
				&& !expectedMigrationPlanFingerprint.equals(execution.migrationPlanFingerprint())) {
			throw new CommandException("Repair evidence does not match expectedMigrationPlanFingerprint.");
		}
		if (expectedRepairPlanFingerprint != null
				&& !expectedRepairPlanFingerprint.equals(execution.repairPlanFingerprint())) {
			throw new CommandException("Repair evidence does not match expectedRepairPlanFingerprint.");
		}
		if (expectedConfigurationFingerprint != null && (execution.provenance() == null
				|| !expectedConfigurationFingerprint.equals(execution.provenance().configurationFingerprint()))) {
			throw new CommandException("Repair evidence does not match expectedConfigurationFingerprint.");
		}
		validateAge(execution.completedAt());
		report = execution;
		postRepairVerificationReport = verification;
		reportFingerprint = executionSnapshot.fingerprint();
		postRepairVerificationReportFingerprint = verificationSnapshot.fingerprint();
		info("Bulk migration job repair evidence verified: ", repairExecutionReportFile.getAbsolutePath());
	}

	private void resolveReportFiles() {
		if (repairReportDirectory == null) {
			return;
		}
		if (!repairReportDirectory.isDirectory()) {
			throw new CommandException("repairReportDirectory must be an existing directory.");
		}
		final var resolved = BulkMigrationJobRepairReportFiles.resolve(repairReportDirectory);
		if (repairExecutionReportFile == null) {
			repairExecutionReportFile = resolved.execution();
		}
		if (postRepairVerificationReportFile == null) {
			postRepairVerificationReportFile = resolved.verification();
		}
	}

	private void verifyExpectedFileFingerprint(final String executionFingerprint,
			final String verificationFingerprint) {
		if (expectedRepairExecutionReportFingerprint != null
				&& !expectedRepairExecutionReportFingerprint.equals(executionFingerprint)) {
			throw new CommandException(
					"repairExecutionReportFile fingerprint does not match expectedRepairExecutionReportFingerprint.");
		}
		if (expectedPostRepairVerificationReportFingerprint != null
				&& !expectedPostRepairVerificationReportFingerprint.equals(verificationFingerprint)) {
			throw new CommandException(
					"postRepairVerificationReportFile fingerprint does not match expectedPostRepairVerificationReportFingerprint.");
		}
	}

	private void validateAge(final Instant completedAt) {
		if (maxEvidenceAgeSeconds == null) {
			return;
		}
		final Instant now = Instant.now();
		if (completedAt.isAfter(now)) {
			throw new CommandException("Repair execution completedAt is in the future; check clock synchronization.");
		}
		try {
			if (completedAt.plusSeconds(maxEvidenceAgeSeconds).isBefore(now)) {
				throw new CommandException("Repair execution evidence has expired.");
			}
		} catch (DateTimeException | ArithmeticException e) {
			throw new CommandException("maxEvidenceAgeSeconds is outside the supported time range.", e);
		}
	}

	private static void requireFile(final File file, final String property) {
		if (file == null || !file.isFile()) {
			throw new CommandException(property + " must be an existing file.");
		}
	}

	private static void validateSha256(final String value, final String property) {
		if (value != null && !value.matches("sha256:[0-9a-f]{64}")) {
			throw new CommandException(property + " must be a lowercase SHA-256 value.");
		}
	}

	private static void validateOptionalText(final String value, final String property) {
		if (value != null && value.isBlank()) {
			throw new CommandException(property + " must not be blank.");
		}
	}

}
