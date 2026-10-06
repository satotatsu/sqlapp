/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.io.File;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.exceptions.CommandException;

import lombok.Getter;
import lombok.Setter;

/** Verifies approved repair failure evidence without database access. */
@Getter
@Setter
public class VerifyBulkMigrationJobRepairFailureEvidenceCommand extends AbstractCommand {
	private File repairReportDirectory;
	private File repairFailureReportFile;
	private File approvedRepairPlanFile;
	private String expectedApprovedRepairPlanFileFingerprint;
	private Long maxApprovedRepairPlanAgeSeconds;
	private Long maxApprovedRepairPlanFileSizeBytes;
	private File postRepairVerificationReportFile;
	private String expectedRepairFailureReportFingerprint;
	private String expectedPostRepairVerificationReportFingerprint;
	private String expectedMigrationPlanFingerprint;
	private String expectedRepairPlanFingerprint;
	private String expectedConfigurationFingerprint;
	private Long maxEvidenceAgeSeconds;
	private Long maxEvidenceFileSizeBytes;
	private BulkMigrationJobRepairFailureReport report;
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
		requireFile(repairFailureReportFile, "repairFailureReportFile");
		requireFile(approvedRepairPlanFile, "approvedRepairPlanFile");
		final var approved = BulkMigrationJobRepairApprovalValidator.validate(approvedRepairPlanFile,
				expectedApprovedRepairPlanFileFingerprint, maxApprovedRepairPlanAgeSeconds,
				maxApprovedRepairPlanFileSizeBytes);
		final var approval = approved.report();
		validateSha256(expectedRepairFailureReportFingerprint, "expectedRepairFailureReportFingerprint");
		validateSha256(expectedPostRepairVerificationReportFingerprint,
				"expectedPostRepairVerificationReportFingerprint");
		validateSha256(expectedConfigurationFingerprint, "expectedConfigurationFingerprint");
		validateOptionalText(expectedMigrationPlanFingerprint, "expectedMigrationPlanFingerprint");
		validateOptionalText(expectedRepairPlanFingerprint, "expectedRepairPlanFingerprint");
		if (maxEvidenceAgeSeconds != null && maxEvidenceAgeSeconds <= 0) {
			throw new CommandException("maxEvidenceAgeSeconds must be greater than zero.");
		}
		final var failureSnapshot = new BulkMigrationJobRepairFailureReportIO()
				.readSnapshot(repairFailureReportFile.toPath(), maxEvidenceFileSizeBytes);
		if (expectedRepairFailureReportFingerprint != null
				&& !expectedRepairFailureReportFingerprint.equals(failureSnapshot.fingerprint())) {
			throw new CommandException(
					"repairFailureReportFile fingerprint does not match expectedRepairFailureReportFingerprint.");
		}
		final var failure = failureSnapshot.report();
		final List<String> approvedTaskIds = approval.tasks().stream()
				.map(BulkMigrationJobRepairPlanReport.Task::taskId).toList();
		final List<String> completedTaskIds = failure.completedTasks().stream()
				.map(BulkMigrationJobRepairExecutionReport.Task::taskId).toList();
		final boolean postVerification = "POST_VERIFICATION".equals(failure.phase());
		if (!approved.fingerprint().equals(failure.approvedRepairPlanFileFingerprint())
				|| !approval.planFingerprint().equals(failure.repairPlanFingerprint())
				|| failure.failedAt().isBefore(approval.generatedAt())
				|| !isCompletedPrefix(approvedTaskIds, completedTaskIds)
				|| (!postVerification && completedTaskIds.contains(failure.failedTaskId()))
				|| (postVerification && (!completedTaskIds.equals(approvedTaskIds)
						|| !approvedTaskIds.contains(failure.failedTaskId())))
				|| (!postVerification && !"PREFLIGHT".equals(failure.phase())
						&& (completedTaskIds.size() >= approvedTaskIds.size()
								|| !approvedTaskIds.get(completedTaskIds.size()).equals(failure.failedTaskId())))) {
			throw new CommandException("Repair failure report does not match approvedRepairPlanFile.");
		}
		if (postVerification) {
			verifyPostRepairVerification(failure, approvedTaskIds);
		}
		if (expectedMigrationPlanFingerprint != null
				&& !expectedMigrationPlanFingerprint.equals(failure.migrationPlanFingerprint())) {
			throw new CommandException("Repair failure evidence does not match expectedMigrationPlanFingerprint.");
		}
		if (expectedRepairPlanFingerprint != null
				&& !expectedRepairPlanFingerprint.equals(failure.repairPlanFingerprint())) {
			throw new CommandException("Repair failure evidence does not match expectedRepairPlanFingerprint.");
		}
		if (expectedConfigurationFingerprint != null && (failure.provenance() == null
				|| !expectedConfigurationFingerprint.equals(failure.provenance().configurationFingerprint()))) {
			throw new CommandException("Repair failure evidence does not match expectedConfigurationFingerprint.");
		}
		validateAge(failure.failedAt());
		report = failure;
		reportFingerprint = failureSnapshot.fingerprint();
		info("Bulk migration job repair failure evidence verified: ", repairFailureReportFile.getAbsolutePath());
	}

	private void resolveReportFiles() {
		if (repairReportDirectory == null) {
			return;
		}
		if (!repairReportDirectory.isDirectory()) {
			throw new CommandException("repairReportDirectory must be an existing directory.");
		}
		final var resolved = BulkMigrationJobRepairReportFiles.resolve(repairReportDirectory);
		if (repairFailureReportFile == null) {
			repairFailureReportFile = resolved.failure();
		}
		if (postRepairVerificationReportFile == null && resolved.verification().isFile()) {
			postRepairVerificationReportFile = resolved.verification();
		}
	}

	private void verifyPostRepairVerification(final BulkMigrationJobRepairFailureReport failure,
			final List<String> approvedTaskIds) {
		if (failure.postRepairVerificationReportFingerprint() == null) {
			if (postRepairVerificationReportFile != null
					|| expectedPostRepairVerificationReportFingerprint != null) {
				throw new CommandException("Repair failure evidence does not reference a post-repair verification report.");
			}
			return;
		}
		requireFile(postRepairVerificationReportFile, "postRepairVerificationReportFile");
		final var verificationSnapshot = new BulkMigrationVerificationReportIO()
				.readSnapshot(postRepairVerificationReportFile.toPath(), maxEvidenceFileSizeBytes);
		if (!verificationSnapshot.fingerprint().equals(failure.postRepairVerificationReportFingerprint())) {
			throw new CommandException("Post-repair verification report does not match repair failure evidence.");
		}
		if (expectedPostRepairVerificationReportFingerprint != null
				&& !expectedPostRepairVerificationReportFingerprint.equals(verificationSnapshot.fingerprint())) {
			throw new CommandException(
					"postRepairVerificationReportFile fingerprint does not match expectedPostRepairVerificationReportFingerprint.");
		}
		final var verification = verificationSnapshot.report();
		if (verification.match() || !failure.migrationPlanFingerprint().equals(verification.planFingerprint())
				|| !approvedTaskIds.equals(verification.tasks().stream()
						.map(BulkMigrationVerificationReport.Task::taskId).toList())
				|| verification.tasks().stream().filter(task -> !task.match())
						.noneMatch(task -> task.taskId().equals(failure.failedTaskId()))
				|| !java.util.Objects.equals(failure.provenance(), verification.provenance())
				|| verification.generatedAt().isAfter(failure.failedAt())) {
			throw new CommandException("Post-repair verification report does not match repair failure evidence.");
		}
		postRepairVerificationReportFingerprint = verificationSnapshot.fingerprint();
		postRepairVerificationReport = verification;
	}

	private static boolean isCompletedPrefix(final List<String> approved, final List<String> completed) {
		return completed.size() <= approved.size() && approved.subList(0, completed.size()).equals(completed);
	}

	private void validateAge(final Instant failedAt) {
		if (maxEvidenceAgeSeconds == null) {
			return;
		}
		final Instant now = Instant.now();
		if (failedAt.isAfter(now)) {
			throw new CommandException("Repair failure failedAt is in the future; check clock synchronization.");
		}
		try {
			if (failedAt.plusSeconds(maxEvidenceAgeSeconds).isBefore(now)) {
				throw new CommandException("Repair failure evidence has expired.");
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
