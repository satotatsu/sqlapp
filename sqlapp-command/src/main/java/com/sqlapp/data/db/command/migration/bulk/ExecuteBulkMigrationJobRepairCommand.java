/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.HashSet;

import javax.sql.DataSource;

import com.sqlapp.data.db.command.AbstractDataSourceCommand;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.bulk.BulkMigrationJobRepairExecutor;
import com.sqlapp.jdbc.bulk.BulkMigrationJobRepairException;
import com.sqlapp.jdbc.bulk.BulkMigrationJobRepairResult;
import com.sqlapp.jdbc.bulk.BulkMigrationJobVerificationResult;

import lombok.Getter;
import lombok.Setter;

/** Re-verifies and executes an explicitly approved declarative job repair plan. */
@Getter
@Setter
public class ExecuteBulkMigrationJobRepairCommand extends AbstractDataSourceCommand {
	private File configurationFile;
	private File repairReportDirectory;
	private String expectedConfigurationFingerprint;
	private Long maxConfigurationFileSizeBytes;
	private File approvedRepairPlanFile;
	private String expectedApprovedRepairPlanFileFingerprint;
	private Long maxApprovedRepairPlanAgeSeconds;
	private Long maxApprovedRepairPlanFileSizeBytes;
	private Long maxEvidenceFileSizeBytes;
	private File repairExecutionReportFile;
	private File repairFailureReportFile;
	private File postRepairVerificationReportFile;
	private File repairOutcomeReportFile;
	private File assessmentReportFile;
	private File ddlVerificationReportFile;
	private File targetValidationReportFile;
	private String expectedTargetValidationReportFingerprint;
	private Long maxTargetValidationAgeSeconds;
	private Long maxTargetValidationReportFileSizeBytes;
	private String targetEnvironmentId;
	private DataSource sourceDataSource;
	private BulkMigrationJobRepairResult result;
	private BulkMigrationJobVerificationResult verificationResult;
	private BulkMigrationTargetValidationReport approvedTargetValidationReport;
	private String approvedTargetValidationReportFingerprint;
	private BulkMigrationJobRepairOutcomeReport outcomeReport;
	private String approvedRepairPlanFileFingerprint;
	private String repairExecutionReportFingerprint;
	private String repairFailureReportFingerprint;
	private String postRepairVerificationReportFingerprint;
	private BulkMigrationJobRepairPlanReport approvedRepairPlanReport;

	@Override
	protected void doRun() {
		result = null;
		verificationResult = null;
		approvedTargetValidationReport = null;
		approvedTargetValidationReportFingerprint = null;
		outcomeReport = null;
		approvedRepairPlanFileFingerprint = null;
		repairExecutionReportFingerprint = null;
		repairFailureReportFingerprint = null;
		postRepairVerificationReportFingerprint = null;
		approvedRepairPlanReport = null;
		resolveReportFiles();
		if (getDataSource() == null) {
			throw new CommandException("Bulk migration target data source is required.");
		}
		if (sourceDataSource == null) {
			throw new CommandException("Bulk migration source data source is required.");
		}
		if (configurationFile == null || !configurationFile.isFile()) {
			throw new CommandException("Bulk migration configuration file is required.");
		}
		if (approvedRepairPlanFile == null || !approvedRepairPlanFile.isFile()) {
			throw new CommandException("Approved bulk migration repair plan file is required.");
		}
		final var approved = BulkMigrationJobRepairApprovalValidator.validate(approvedRepairPlanFile,
				expectedApprovedRepairPlanFileFingerprint, maxApprovedRepairPlanAgeSeconds,
				maxApprovedRepairPlanFileSizeBytes);
		approvedRepairPlanReport = approved.report();
		approvedRepairPlanFileFingerprint = approved.fingerprint();
		validateArtifactPaths();
		BulkMigrationExecutionApprovalValidator.validateConfigurationFingerprint(configurationFile,
				expectedConfigurationFingerprint);
		BulkMigrationExecutionApprovalValidator.validateConfigurationFileSize(configurationFile,
				maxConfigurationFileSizeBytes);
		BulkMigrationExecutionApprovalValidator.validateArtifactInputs(configurationFile, assessmentReportFile,
				ddlVerificationReportFile);
		BulkMigrationExecutionApprovalValidator.validateTargetInputs(configurationFile, targetValidationReportFile,
				expectedTargetValidationReportFingerprint, maxTargetValidationAgeSeconds,
				maxTargetValidationReportFileSizeBytes, targetEnvironmentId);
		executeNoTranAndClose(sourceDataSource, sourceConnection -> {
			final var resolved = new BulkMigrationJobConfigurationResolver().resolveJob(configurationFile,
					sourceConnection, expectedConfigurationFingerprint, maxConfigurationFileSizeBytes);
			BulkMigrationExecutionApprovalValidator.validateArtifacts(resolved.provenance(), assessmentReportFile,
					ddlVerificationReportFile);
			final var approvedTarget = BulkMigrationExecutionApprovalValidator.validateTargetReport(
					targetValidationReportFile, expectedTargetValidationReportFingerprint, targetEnvironmentId,
					maxTargetValidationAgeSeconds == null ? 0 : maxTargetValidationAgeSeconds,
					maxTargetValidationReportFileSizeBytes, resolved);
			approvedTargetValidationReport = approvedTarget == null ? null : approvedTarget.report();
			approvedTargetValidationReportFingerprint = approvedTarget == null ? null : approvedTarget.fingerprint();
			if (resolved.verificationConfiguration() == null) {
				throw new CommandException("Bulk migration verification must be enabled to execute repair.");
			}
			executeNoTranAndClose(getDataSource(), targetConnection -> executeRepair(resolved, targetConnection));
		});
		info("Bulk migration job repair completed: ", result.getPlanFingerprint());
	}

	private void resolveReportFiles() {
		if (repairReportDirectory == null) {
			return;
		}
		final Path directory = repairReportDirectory.toPath().toAbsolutePath().normalize();
		if (Files.exists(directory) && !Files.isDirectory(directory)) {
			throw new CommandException("repairReportDirectory must be a directory path.");
		}
		final var resolved = BulkMigrationJobRepairReportFiles.resolve(repairReportDirectory);
		if (repairExecutionReportFile == null) {
			repairExecutionReportFile = resolved.execution();
		}
		if (repairFailureReportFile == null) {
			repairFailureReportFile = resolved.failure();
		}
		if (postRepairVerificationReportFile == null) {
			postRepairVerificationReportFile = resolved.verification();
		}
		if (repairOutcomeReportFile == null) {
			repairOutcomeReportFile = resolved.outcome();
		}
	}

	private void executeRepair(final BulkMigrationJobConfigurationResolver.Resolution resolved,
			final Connection targetConnection) throws Exception {
		BulkMigrationExecutionApprovalValidator.validateTargetDatabaseIdentity(approvedTargetValidationReport,
				targetConnection);
		final var verification = resolved.verificationConfiguration();
		final var verified = ExecuteBulkMigrationJobCommand.verifyWithIsolation(resolved.plan(), targetConnection,
				verification.chunkSize(), verification.columnsByTask(), verification.isolation());
		final var plan = ExecuteBulkMigrationJobCommand.repairPlan(resolved.plan(), targetConnection, verified);
		if (!plan.getFingerprint().equals(approvedRepairPlanReport.planFingerprint())) {
			throw new CommandException("Bulk migration job repair plan fingerprint mismatch");
		}
		final var executionProvenance = BulkMigrationExecutionApprovalValidator.executionProvenance(
				resolved.provenance(), approvedTargetValidationReportFingerprint);
		clearPreviousOutcomeArtifacts();
		try {
			result = BulkMigrationJobRepairExecutor.execute(targetConnection, plan,
					approvedRepairPlanReport.planFingerprint());
		} catch (BulkMigrationJobRepairException failure) {
			if (repairFailureReportFile != null) {
				try {
					final var failureIO = new BulkMigrationJobRepairFailureReportIO();
					final var failureSnapshot = failureIO.writeSnapshot(repairFailureReportFile.toPath(),
							failureIO.fromFailure(resolved.plan().getFingerprint(),
									approvedRepairPlanFileFingerprint, failure, executionProvenance));
					repairFailureReportFingerprint = failureSnapshot.fingerprint();
					publishOutcome();
				} catch (RuntimeException evidenceFailure) {
					failure.addSuppressed(evidenceFailure);
				}
			}
			throw failure;
		}
		if (repairExecutionReportFile != null) {
			final var executionIO = new BulkMigrationJobRepairExecutionReportIO();
			final var executionSnapshot = executionIO.writeSnapshot(repairExecutionReportFile.toPath(),
					executionIO.fromResult(resolved.plan().getFingerprint(), approvedRepairPlanFileFingerprint,
						result, executionProvenance));
			repairExecutionReportFingerprint = executionSnapshot.fingerprint();
		}
		verificationResult = ExecuteBulkMigrationJobCommand.verifyWithIsolation(resolved.plan(), targetConnection,
				verification.chunkSize(), verification.columnsByTask(), verification.isolation());
		if (postRepairVerificationReportFile != null) {
			final var verificationIO = new BulkMigrationVerificationReportIO();
			final var verificationReport = verificationIO.fromResult(resolved.plan().getFingerprint(),
					verification.isolation(), verification.maxReportedMismatches(), verificationResult,
					executionProvenance);
			final var verificationSnapshot = verificationIO.writeSnapshot(postRepairVerificationReportFile.toPath(),
					verificationReport);
			postRepairVerificationReportFingerprint = verificationSnapshot.fingerprint();
		}
		if (!verificationResult.isMatch()) {
			final var failure = new CommandException("Bulk migration repair verification failed: "
					+ verificationResult.getMismatchedTasks() + " task(s) mismatched.");
			if (repairFailureReportFile != null) {
				try {
					final var failureIO = new BulkMigrationJobRepairFailureReportIO();
					final var failureSnapshot = failureIO.writeSnapshot(repairFailureReportFile.toPath(),
							failureIO.fromVerificationFailure(
									resolved.plan().getFingerprint(),
									approvedRepairPlanFileFingerprint,
									postRepairVerificationReportFingerprint, result, verificationResult,
									executionProvenance));
					repairFailureReportFingerprint = failureSnapshot.fingerprint();
					publishOutcome();
				} catch (RuntimeException evidenceFailure) {
					failure.addSuppressed(evidenceFailure);
				}
			}
			throw failure;
		}
		publishOutcome();
	}

	private void publishOutcome() {
		if (repairOutcomeReportFile == null) {
			return;
		}
		final var verifier = new VerifyBulkMigrationJobRepairOutcomeCommand();
		verifier.setApprovedRepairPlanFile(approvedRepairPlanFile);
		verifier.setExpectedApprovedRepairPlanFileFingerprint(approvedRepairPlanFileFingerprint);
		verifier.setMaxApprovedRepairPlanAgeSeconds(maxApprovedRepairPlanAgeSeconds);
		verifier.setMaxApprovedRepairPlanFileSizeBytes(maxApprovedRepairPlanFileSizeBytes);
		verifier.setMaxEvidenceFileSizeBytes(maxEvidenceFileSizeBytes);
		verifier.setRepairExecutionReportFile(repairExecutionReportFile);
		verifier.setRepairFailureReportFile(repairFailureReportFile);
		verifier.setPostRepairVerificationReportFile(postRepairVerificationReportFile);
		verifier.setExpectedRepairExecutionReportFingerprint(repairExecutionReportFingerprint);
		verifier.setExpectedRepairFailureReportFingerprint(repairFailureReportFingerprint);
		verifier.setExpectedPostRepairVerificationReportFingerprint(postRepairVerificationReportFingerprint);
		verifier.setOutcomeReportFile(repairOutcomeReportFile);
		verifier.run();
		outcomeReport = verifier.getOutcomeReport();
	}

	private void validateArtifactPaths() {
		final var paths = new HashSet<Path>();
		for (final File input : new File[] { configurationFile, approvedRepairPlanFile }) {
			paths.add(input.toPath().toAbsolutePath().normalize());
		}
		for (final File input : new File[] { assessmentReportFile, ddlVerificationReportFile,
				targetValidationReportFile }) {
			if (input != null) {
				paths.add(input.toPath().toAbsolutePath().normalize());
			}
		}
		if (repairOutcomeReportFile != null && (repairExecutionReportFile == null || repairFailureReportFile == null
				|| postRepairVerificationReportFile == null)) {
			throw new CommandException("repairOutcomeReportFile requires repairExecutionReportFile, "
					+ "repairFailureReportFile and postRepairVerificationReportFile.");
		}
		for (final File output : new File[] { repairExecutionReportFile, repairFailureReportFile,
				postRepairVerificationReportFile, repairOutcomeReportFile }) {
			if (output != null && !paths.add(output.toPath().toAbsolutePath().normalize())) {
				throw new CommandException("Bulk migration repair artifact files must use distinct paths.");
			}
		}
	}

	private void clearPreviousOutcomeArtifacts() {
		for (final File file : new File[] { repairExecutionReportFile, repairFailureReportFile,
				postRepairVerificationReportFile, repairOutcomeReportFile }) {
			if (file == null) {
				continue;
			}
			try {
				Files.deleteIfExists(file.toPath().toAbsolutePath().normalize());
			} catch (IOException e) {
				throw new CommandException("Failed to clear previous bulk migration repair artifact: " + file, e);
			}
		}
	}

}
