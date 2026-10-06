/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.exceptions.CommandException;

import lombok.Getter;
import lombok.Setter;

/** Verifies a completed bulk migration evidence set without database access. */
@Getter
@Setter
public class VerifyBulkMigrationEvidenceCommand extends AbstractCommand {
	private File operationalReportFile;
	private File verificationReportFile;
	private File configurationFile;
	private File assessmentReportFile;
	private File ddlVerificationReportFile;
	private File targetValidationReportFile;
	private String expectedTargetEnvironmentId;
	private File outputFile;
	private boolean requireSuccessfulExecution = true;
	private boolean requireMatchingData = true;
	private boolean requireProvenance = true;
	private Long maxEvidenceFileSizeBytes;
	private Long maxApprovalArtifactFileSizeBytes;
	private Long maxTargetValidationReportFileSizeBytes;
	private BulkMigrationOperationalReport operationalReport;
	private BulkMigrationVerificationReport verificationReport;
	private BulkMigrationEvidenceReport evidenceReport;
	private String evidenceReportFingerprint;

	@Override
	protected void doRun() {
		operationalReport = null;
		verificationReport = null;
		evidenceReport = null;
		evidenceReportFingerprint = null;
		requireFile(operationalReportFile, "operationalReportFile");
		requireFile(verificationReportFile, "verificationReportFile");
		optionalFile(configurationFile, "configurationFile");
		optionalFile(assessmentReportFile, "assessmentReportFile");
		optionalFile(ddlVerificationReportFile, "ddlVerificationReportFile");
		optionalFile(targetValidationReportFile, "targetValidationReportFile");
		validateExpectedTargetEnvironment();
		validateLimits();
		validateOutputFile();
		final var operationalSnapshot = new BulkMigrationOperationalReportIO()
				.readSnapshot(operationalReportFile.toPath(), maxEvidenceFileSizeBytes);
		final var verificationSnapshot = new BulkMigrationVerificationReportIO()
				.readSnapshot(verificationReportFile.toPath(), maxEvidenceFileSizeBytes);
		operationalReport = operationalSnapshot.report();
		verificationReport = verificationSnapshot.report();
		if (!operationalReport.planFingerprint().equals(verificationReport.planFingerprint())) {
			throw new CommandException("Bulk migration evidence plan fingerprints do not match.");
		}
		if (requireSuccessfulExecution && (operationalReport.execution() == null
				|| operationalReport.execution().event() != BulkMigrationOperationalReport.ExecutionEvent.JOB_COMPLETED)) {
			throw new CommandException("Operational report does not contain a completed bulk migration job.");
		}
		if (requireMatchingData && !verificationReport.match()) {
			throw new CommandException("Bulk migration verification report contains data mismatches.");
		}
		final BulkMigrationArtifactProvenance provenance = operationalReport.provenance();
		if (!Objects.equals(provenance, verificationReport.provenance())) {
			throw new CommandException("Bulk migration evidence provenance does not match.");
		}
		if (requireProvenance && provenance == null) {
			throw new CommandException("Bulk migration evidence does not contain provenance.");
		}
		final var targetValidationSnapshot = targetValidationReportFile == null ? null
				: new BulkMigrationTargetValidationReportIO().readSnapshot(targetValidationReportFile.toPath(),
						maxTargetValidationReportFileSizeBytes);
		BulkMigrationArtifactProvenanceVerifier.verify(configurationFile,
				provenance == null ? null : provenance.configurationFingerprint(),
				"configurationFile", "configurationFingerprint", maxApprovalArtifactFileSizeBytes);
		BulkMigrationArtifactProvenanceVerifier.verify(assessmentReportFile,
				provenance == null ? null : provenance.assessmentReportFingerprint(),
				"assessmentReportFile", "assessmentReportFingerprint", maxApprovalArtifactFileSizeBytes);
		BulkMigrationArtifactProvenanceVerifier.verify(ddlVerificationReportFile,
				provenance == null ? null : provenance.ddlVerificationReportFingerprint(),
				"ddlVerificationReportFile", "ddlVerificationReportFingerprint", maxApprovalArtifactFileSizeBytes);
		verifyTargetValidationFingerprint(targetValidationSnapshot,
				provenance == null ? null : provenance.targetValidationReportFingerprint());
		verifyTargetValidationReport(provenance,
				targetValidationSnapshot == null ? null : targetValidationSnapshot.report());
		evidenceReport = evidenceReport(provenance, operationalSnapshot.fingerprint(), verificationSnapshot.fingerprint());
		if (outputFile != null) {
			evidenceReportFingerprint = new BulkMigrationEvidenceReportIO()
					.writeSnapshot(outputFile.toPath(), evidenceReport, maxEvidenceFileSizeBytes).fingerprint();
		}
		info("Bulk migration evidence verified: ", operationalReportFile.getAbsolutePath());
	}

	private BulkMigrationEvidenceReport evidenceReport(final BulkMigrationArtifactProvenance provenance,
			final String operationalFingerprint, final String verificationFingerprint) {
		final List<String> policies = new ArrayList<>();
		if (requireSuccessfulExecution) { policies.add(BulkMigrationEvidenceReport.POLICY_SUCCESSFUL_EXECUTION); }
		if (requireMatchingData) { policies.add(BulkMigrationEvidenceReport.POLICY_MATCHING_DATA); }
		if (requireProvenance) { policies.add(BulkMigrationEvidenceReport.POLICY_PROVENANCE_REQUIRED); }
		final List<String> artifacts = new ArrayList<>(List.of(
				BulkMigrationEvidenceReport.ARTIFACT_OPERATIONAL_REPORT,
				BulkMigrationEvidenceReport.ARTIFACT_VERIFICATION_REPORT));
		if (configurationFile != null) { artifacts.add(BulkMigrationEvidenceReport.ARTIFACT_CONFIGURATION); }
		if (assessmentReportFile != null) { artifacts.add(BulkMigrationEvidenceReport.ARTIFACT_ASSESSMENT_REPORT); }
		if (ddlVerificationReportFile != null) {
			artifacts.add(BulkMigrationEvidenceReport.ARTIFACT_DDL_VERIFICATION_REPORT);
		}
		if (targetValidationReportFile != null) {
			artifacts.add(BulkMigrationEvidenceReport.ARTIFACT_TARGET_VALIDATION_REPORT);
		}
		return new BulkMigrationEvidenceReport(BulkMigrationEvidenceReport.CURRENT_FORMAT_VERSION, Instant.now(),
				operationalReport.jobId(), operationalReport.planFingerprint(), operationalFingerprint,
				verificationFingerprint,
				operationalReport.execution() == null ? null : operationalReport.execution().event(),
				verificationReport.match(),
				provenance, policies, artifacts);
	}

	private void verifyTargetValidationReport(final BulkMigrationArtifactProvenance provenance,
			final BulkMigrationTargetValidationReport report) {
		if (report == null) {
			return;
		}
		final var approvalProvenance = provenance == null ? null : new BulkMigrationArtifactProvenance(
				provenance.configurationFingerprint(), provenance.assessmentReportFingerprint(),
				provenance.ddlVerificationReportFingerprint());
		final List<String> executedTaskIds = operationalReport.tasks().stream()
				.map(BulkMigrationOperationalReport.Task::taskId).toList();
		if (!operationalReport.jobId().equals(report.jobId())
				|| !operationalReport.planFingerprint().equals(report.planFingerprint())
				|| !executedTaskIds.equals(report.taskIds())
				|| report.generatedAt().isAfter(operationalReport.generatedAt())
				|| provenance == null
				|| !provenance.configurationFingerprint().equals(report.configurationFingerprint())
				|| !Objects.equals(approvalProvenance, report.provenance())
				|| expectedTargetEnvironmentId != null
						&& !expectedTargetEnvironmentId.equals(report.targetEnvironmentId())) {
			throw new CommandException(
					"Target validation report does not match the migration evidence or was created after execution.");
		}
	}

	private static void verifyTargetValidationFingerprint(
			final BulkMigrationTargetValidationReportIO.Snapshot snapshot, final String expectedFingerprint) {
		if (snapshot != null && (expectedFingerprint == null || !expectedFingerprint.equals(snapshot.fingerprint()))) {
			throw new CommandException(
					"targetValidationReportFile does not match targetValidationReportFingerprint.");
		}
	}

	private void validateExpectedTargetEnvironment() {
		if (expectedTargetEnvironmentId == null) {
			return;
		}
		if (expectedTargetEnvironmentId.isBlank()) {
			throw new CommandException("expectedTargetEnvironmentId must not be blank.");
		}
		if (targetValidationReportFile == null) {
			throw new CommandException(
					"expectedTargetEnvironmentId requires targetValidationReportFile.");
		}
	}

	private void validateLimits() {
		positive(maxEvidenceFileSizeBytes, "maxEvidenceFileSizeBytes");
		positive(maxApprovalArtifactFileSizeBytes, "maxApprovalArtifactFileSizeBytes");
		positive(maxTargetValidationReportFileSizeBytes, "maxTargetValidationReportFileSizeBytes");
	}

	private static void positive(final Long value, final String property) {
		if (value != null && value <= 0) {
			throw new CommandException(property + " must be greater than zero.");
		}
	}

	private void validateOutputFile() {
		if (outputFile == null) { return; }
		final var output = outputFile.toPath().toAbsolutePath().normalize();
		for (final File input : List.of(operationalReportFile, verificationReportFile)) {
			if (output.equals(input.toPath().toAbsolutePath().normalize())) {
				throw new CommandException("outputFile must not overwrite an input report.");
			}
		}
		if (outputFile.isDirectory()) {
			throw new CommandException("outputFile must be a JSON file path.");
		}
	}

	private static void requireFile(final File file, final String property) {
		if (file == null || !file.isFile()) {
			throw new CommandException(property + " must be an existing file.");
		}
	}

	private static void optionalFile(final File file, final String property) {
		if (file != null && !file.isFile()) {
			throw new CommandException(property + " must be an existing file.");
		}
	}

}
