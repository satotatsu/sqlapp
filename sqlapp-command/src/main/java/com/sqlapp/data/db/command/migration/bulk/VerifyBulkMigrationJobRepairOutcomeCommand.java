/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.exceptions.CommandException;

import lombok.Getter;
import lombok.Setter;

/** Selects and verifies the saved outcome of one approved repair without database access. */
@Getter
@Setter
public class VerifyBulkMigrationJobRepairOutcomeCommand extends AbstractCommand {
	public enum Status { SUCCEEDED, EXECUTION_FAILED, VERIFICATION_FAILED }

	private File approvedRepairPlanFile;
	private String expectedApprovedRepairPlanFileFingerprint;
	private Long maxApprovedRepairPlanAgeSeconds;
	private Long maxApprovedRepairPlanFileSizeBytes;
	private File repairReportDirectory;
	private File repairExecutionReportFile;
	private File repairFailureReportFile;
	private File postRepairVerificationReportFile;
	private String expectedRepairExecutionReportFingerprint;
	private String expectedRepairFailureReportFingerprint;
	private String expectedPostRepairVerificationReportFingerprint;
	private String expectedMigrationPlanFingerprint;
	private String expectedRepairPlanFingerprint;
	private String expectedConfigurationFingerprint;
	private String expectedStatus;
	private Long maxEvidenceAgeSeconds;
	private Long maxEvidenceFileSizeBytes;
	private File outcomeReportFile;
	private Status status;
	private BulkMigrationJobRepairExecutionReport executionReport;
	private BulkMigrationJobRepairFailureReport failureReport;
	private BulkMigrationJobRepairOutcomeReport outcomeReport;
	private BulkMigrationVerificationReport postRepairVerificationReport;
	private String approvedRepairPlanFingerprint;
	private String executionReportFingerprint;
	private String failureReportFingerprint;
	private String postRepairVerificationReportFingerprint;

	@Override
	protected void doRun() {
		status = null;
		executionReport = null;
		failureReport = null;
		outcomeReport = null;
		approvedRepairPlanFingerprint = null;
		executionReportFingerprint = null;
		failureReportFingerprint = null;
		postRepairVerificationReportFingerprint = null;
		postRepairVerificationReport = null;
		resolveReportFiles();
		if (approvedRepairPlanFile == null || !approvedRepairPlanFile.isFile()) {
			throw new CommandException("approvedRepairPlanFile must be an existing file.");
		}
		final var approved = BulkMigrationJobRepairApprovalValidator.validate(approvedRepairPlanFile,
				expectedApprovedRepairPlanFileFingerprint, maxApprovedRepairPlanAgeSeconds,
				maxApprovedRepairPlanFileSizeBytes);
		approvedRepairPlanFingerprint = approved.fingerprint();
		validateExpectedStatus();
		prepareOutput();
		if (repairFailureReportFile != null && repairFailureReportFile.isFile()) {
			final var verifier = new VerifyBulkMigrationJobRepairFailureEvidenceCommand();
			verifier.setApprovedRepairPlanFile(approvedRepairPlanFile);
			verifier.setExpectedApprovedRepairPlanFileFingerprint(approvedRepairPlanFingerprint);
			verifier.setMaxApprovedRepairPlanAgeSeconds(maxApprovedRepairPlanAgeSeconds);
			verifier.setMaxApprovedRepairPlanFileSizeBytes(maxApprovedRepairPlanFileSizeBytes);
			verifier.setRepairFailureReportFile(repairFailureReportFile);
			verifier.setPostRepairVerificationReportFile(postRepairVerificationReportFile);
			verifier.setExpectedRepairFailureReportFingerprint(expectedRepairFailureReportFingerprint);
			verifier.setExpectedPostRepairVerificationReportFingerprint(
					expectedPostRepairVerificationReportFingerprint);
			copyCommon(verifier);
			verifier.run();
			final var verifiedFailure = verifier.getReport();
			final Status selectedStatus = "POST_VERIFICATION".equals(verifiedFailure.phase())
					? Status.VERIFICATION_FAILED : Status.EXECUTION_FAILED;
			verifyFailureCompanions(selectedStatus, verifiedFailure);
			failureReport = verifiedFailure;
			failureReportFingerprint = verifier.getReportFingerprint();
			postRepairVerificationReportFingerprint = verifier.getPostRepairVerificationReportFingerprint();
			postRepairVerificationReport = verifier.getPostRepairVerificationReport();
			status = selectedStatus;
			publishOutcome();
			info("Bulk migration job repair outcome verified: ", status);
			enforceExpectedStatus();
			return;
		}
		if (repairExecutionReportFile == null || !repairExecutionReportFile.isFile()
				|| postRepairVerificationReportFile == null || !postRepairVerificationReportFile.isFile()) {
			throw new CommandException("Repair outcome requires either an existing failure report or both successful "
					+ "execution and post-repair verification reports.");
		}
		final var verifier = new VerifyBulkMigrationJobRepairEvidenceCommand();
		verifier.setApprovedRepairPlanFile(approvedRepairPlanFile);
		verifier.setExpectedApprovedRepairPlanFileFingerprint(approvedRepairPlanFingerprint);
		verifier.setMaxApprovedRepairPlanAgeSeconds(maxApprovedRepairPlanAgeSeconds);
		verifier.setMaxApprovedRepairPlanFileSizeBytes(maxApprovedRepairPlanFileSizeBytes);
		verifier.setRepairExecutionReportFile(repairExecutionReportFile);
		verifier.setPostRepairVerificationReportFile(postRepairVerificationReportFile);
		verifier.setExpectedRepairExecutionReportFingerprint(expectedRepairExecutionReportFingerprint);
		verifier.setExpectedPostRepairVerificationReportFingerprint(expectedPostRepairVerificationReportFingerprint);
		verifier.setExpectedMigrationPlanFingerprint(expectedMigrationPlanFingerprint);
		verifier.setExpectedRepairPlanFingerprint(expectedRepairPlanFingerprint);
		verifier.setExpectedConfigurationFingerprint(expectedConfigurationFingerprint);
		verifier.setMaxEvidenceAgeSeconds(maxEvidenceAgeSeconds);
		verifier.setMaxEvidenceFileSizeBytes(maxEvidenceFileSizeBytes);
		verifier.run();
		executionReport = verifier.getReport();
		executionReportFingerprint = verifier.getReportFingerprint();
		postRepairVerificationReportFingerprint = verifier.getPostRepairVerificationReportFingerprint();
		postRepairVerificationReport = verifier.getPostRepairVerificationReport();
		status = Status.SUCCEEDED;
		publishOutcome();
		info("Bulk migration job repair outcome verified: ", status);
		enforceExpectedStatus();
	}

	private void resolveReportFiles() {
		if (repairReportDirectory == null) {
			return;
		}
		if (!repairReportDirectory.isDirectory()) {
			throw new CommandException("repairReportDirectory must be an existing directory.");
		}
		final var resolved = BulkMigrationJobRepairReportFiles.resolve(repairReportDirectory);
		if (repairExecutionReportFile == null) { repairExecutionReportFile = resolved.execution(); }
		if (repairFailureReportFile == null) { repairFailureReportFile = resolved.failure(); }
		if (postRepairVerificationReportFile == null) { postRepairVerificationReportFile = resolved.verification(); }
		if (outcomeReportFile == null) { outcomeReportFile = resolved.outcome(); }
	}

	private void validateExpectedStatus() {
		if (expectedStatus == null) {
			return;
		}
		try {
			Status.valueOf(expectedStatus);
		} catch (IllegalArgumentException e) {
			throw new CommandException("expectedStatus must be SUCCEEDED, EXECUTION_FAILED or VERIFICATION_FAILED.");
		}
	}

	private void enforceExpectedStatus() {
		if (expectedStatus != null && !expectedStatus.equals(status.name())) {
			throw new CommandException("Repair outcome status " + status + " does not match expectedStatus "
					+ expectedStatus + ".");
		}
	}

	private void publishOutcome() {
		try {
			final String migrationFingerprint = executionReport != null ? executionReport.migrationPlanFingerprint()
					: failureReport.migrationPlanFingerprint();
			final String repairFingerprint = executionReport != null ? executionReport.repairPlanFingerprint()
					: failureReport.repairPlanFingerprint();
			final var provenance = executionReport != null ? executionReport.provenance() : failureReport.provenance();
			final var report = new BulkMigrationJobRepairOutcomeReport(
					BulkMigrationJobRepairOutcomeReport.CURRENT_FORMAT_VERSION, Instant.now(), status.name(),
					migrationFingerprint, repairFingerprint, approvedRepairPlanFingerprint, executionReportFingerprint,
					failureReportFingerprint, postRepairVerificationReportFingerprint,
					failureReport == null ? null : failureReport.phase(),
					failureReport == null ? null : failureReport.failedTaskId(), provenance);
			outcomeReport = BulkMigrationJobRepairOutcomeReportIO.validate(report);
			if (outcomeReportFile != null) {
				validateOutputPath();
				new BulkMigrationJobRepairOutcomeReportIO().write(outcomeReportFile.toPath(), outcomeReport);
			}
		} catch (RuntimeException e) {
			status = null;
			executionReport = null;
			failureReport = null;
			outcomeReport = null;
			postRepairVerificationReport = null;
			approvedRepairPlanFingerprint = null;
			executionReportFingerprint = null;
			failureReportFingerprint = null;
			postRepairVerificationReportFingerprint = null;
			throw e;
		}
	}

	private void validateOutputPath() {
		final var output = outcomeReportFile.toPath().toAbsolutePath().normalize();
		for (final File input : new File[] { approvedRepairPlanFile, repairExecutionReportFile,
				repairFailureReportFile, postRepairVerificationReportFile }) {
			if (input != null && output.equals(input.toPath().toAbsolutePath().normalize())) {
				throw new CommandException("outcomeReportFile must not overwrite an input report.");
			}
		}
	}

	private void prepareOutput() {
		if (outcomeReportFile == null) {
			return;
		}
		validateOutputPath();
		try {
			Files.deleteIfExists(outcomeReportFile.toPath().toAbsolutePath().normalize());
		} catch (IOException e) {
			throw new CommandException("Failed to remove stale bulk migration job repair outcome report: "
					+ outcomeReportFile, e);
		}
	}

	private void verifyFailureCompanions(final Status selectedStatus,
			final BulkMigrationJobRepairFailureReport verifiedFailure) {
		final boolean hasExecution = repairExecutionReportFile != null && repairExecutionReportFile.isFile();
		final boolean hasVerification = postRepairVerificationReportFile != null
				&& postRepairVerificationReportFile.isFile();
		if (selectedStatus == Status.EXECUTION_FAILED) {
			if (hasExecution || hasVerification || expectedRepairExecutionReportFingerprint != null
					|| expectedPostRepairVerificationReportFingerprint != null) {
				throw new CommandException("Execution failure evidence conflicts with successful repair artifacts.");
			}
			return;
		}
		if (expectedRepairExecutionReportFingerprint != null && !hasExecution) {
			throw new CommandException(
					"expectedRepairExecutionReportFingerprint requires an existing repairExecutionReportFile.");
		}
		if (!hasExecution) {
			return;
		}
		if (!hasVerification) {
			throw new CommandException(
					"Post-verification failure execution evidence requires postRepairVerificationReportFile.");
		}
		final var executionSnapshot = new BulkMigrationJobRepairExecutionReportIO()
				.readSnapshot(repairExecutionReportFile.toPath(), maxEvidenceFileSizeBytes);
		final var verificationSnapshot = new BulkMigrationVerificationReportIO()
				.readSnapshot(postRepairVerificationReportFile.toPath(), maxEvidenceFileSizeBytes);
		if (expectedRepairExecutionReportFingerprint != null
				&& !expectedRepairExecutionReportFingerprint.equals(executionSnapshot.fingerprint())) {
			throw new CommandException(
					"repairExecutionReportFile fingerprint does not match expectedRepairExecutionReportFingerprint.");
		}
		final var execution = executionSnapshot.report();
		final var verification = verificationSnapshot.report();
		if (!execution.migrationPlanFingerprint().equals(verifiedFailure.migrationPlanFingerprint())
				|| !execution.repairPlanFingerprint().equals(verifiedFailure.repairPlanFingerprint())
				|| !execution.approvedRepairPlanFileFingerprint()
						.equals(verifiedFailure.approvedRepairPlanFileFingerprint())
				|| !execution.tasks().equals(verifiedFailure.completedTasks())
				|| !java.util.Objects.equals(execution.provenance(), verifiedFailure.provenance())
				|| execution.completedAt().isAfter(verification.generatedAt())
				|| execution.completedAt().isAfter(verifiedFailure.failedAt())) {
			throw new CommandException("Repair execution report does not match post-verification failure evidence.");
		}
		executionReport = execution;
		executionReportFingerprint = executionSnapshot.fingerprint();
		postRepairVerificationReportFingerprint = verificationSnapshot.fingerprint();
		postRepairVerificationReport = verification;
	}

	private void copyCommon(final VerifyBulkMigrationJobRepairFailureEvidenceCommand verifier) {
		verifier.setExpectedMigrationPlanFingerprint(expectedMigrationPlanFingerprint);
		verifier.setExpectedRepairPlanFingerprint(expectedRepairPlanFingerprint);
		verifier.setExpectedConfigurationFingerprint(expectedConfigurationFingerprint);
		verifier.setMaxEvidenceAgeSeconds(maxEvidenceAgeSeconds);
		verifier.setMaxEvidenceFileSizeBytes(maxEvidenceFileSizeBytes);
	}
}
