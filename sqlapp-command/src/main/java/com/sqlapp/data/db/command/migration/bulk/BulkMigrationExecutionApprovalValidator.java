/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import com.sqlapp.exceptions.CommandException;

/**
 * Shared approval validation for declarative migration and repair execution.
 */
final class BulkMigrationExecutionApprovalValidator {
	private BulkMigrationExecutionApprovalValidator() {
	}

	static void validateConfigurationInputs(final File configurationFile, final String expectedFingerprint,
			final Long maxFileSizeBytes) {
		if (configurationFile == null && (expectedFingerprint != null || maxFileSizeBytes != null)) {
			throw new CommandException("Configuration approval properties require configurationFile.");
		}
		if (expectedFingerprint != null && !expectedFingerprint.matches("sha256:[0-9a-f]{64}")) {
			throw new CommandException("expectedConfigurationFingerprint must be a lowercase SHA-256 value.");
		}
		if (maxFileSizeBytes != null && maxFileSizeBytes <= 0) {
			throw new CommandException("maxConfigurationFileSizeBytes must be greater than zero.");
		}
	}

	static void validateArtifactInputs(final File configurationFile, final File assessmentReportFile,
			final File ddlVerificationReportFile, final Long maxFileSizeBytes) {
		if ((assessmentReportFile != null || ddlVerificationReportFile != null) && configurationFile == null) {
			throw new CommandException("Approval artifact files require configurationFile.");
		}
		validateArtifactFile(assessmentReportFile, "assessmentReportFile");
		validateArtifactFile(ddlVerificationReportFile, "ddlVerificationReportFile");
		if (maxFileSizeBytes != null && maxFileSizeBytes <= 0) {
			throw new CommandException("maxApprovalArtifactFileSizeBytes must be greater than zero.");
		}
	}

	static void validateTargetInputs(final File configurationFile, final File targetValidationReportFile,
			final String expectedFingerprint, final Long maxAgeSeconds, final Long maxFileSizeBytes,
			final String targetEnvironmentId) {
		if (targetValidationReportFile == null) {
			if (expectedFingerprint != null || maxAgeSeconds != null || maxFileSizeBytes != null
					|| targetEnvironmentId != null) {
				throw new CommandException("Target validation approval properties require targetValidationReportFile.");
			}
			return;
		}
		validateArtifactFile(targetValidationReportFile, "targetValidationReportFile");
		if (configurationFile == null) {
			throw new CommandException("targetValidationReportFile requires configurationFile.");
		}
		if (maxAgeSeconds == null || maxAgeSeconds <= 0) {
			throw new CommandException(
					"maxTargetValidationAgeSeconds must be greater than zero when targetValidationReportFile is set.");
		}
		if (maxFileSizeBytes != null && maxFileSizeBytes <= 0) {
			throw new CommandException("maxTargetValidationReportFileSizeBytes must be greater than zero.");
		}
		if (targetEnvironmentId != null && targetEnvironmentId.isBlank()) {
			throw new CommandException("targetEnvironmentId must not be blank.");
		}
		if (expectedFingerprint != null && !expectedFingerprint.matches("sha256:[0-9a-f]{64}")) {
			throw new CommandException("expectedTargetValidationReportFingerprint must be a lowercase SHA-256 value.");
		}
	}

	static void validateArtifacts(final BulkMigrationArtifactProvenance provenance, final File assessmentReportFile,
			final File ddlVerificationReportFile, final Long maxFileSizeBytes) {
		BulkMigrationArtifactProvenanceVerifier.verify(assessmentReportFile,
				provenance == null ? null : provenance.assessmentReportFingerprint(), "assessmentReportFile",
				"assessmentReportFingerprint", maxFileSizeBytes);
		BulkMigrationArtifactProvenanceVerifier.verify(ddlVerificationReportFile,
				provenance == null ? null : provenance.ddlVerificationReportFingerprint(), "ddlVerificationReportFile",
				"ddlVerificationReportFingerprint", maxFileSizeBytes);
	}

	static ValidatedTargetReport validateTargetReport(final File targetValidationReportFile,
			final String expectedFingerprint, final String targetEnvironmentId, final long maxAgeSeconds,
			final Long maxFileSizeBytes, final BulkMigrationJobConfigurationResolver.Resolution resolved) {
		if (targetValidationReportFile == null) {
			return null;
		}
		final var snapshot = new BulkMigrationTargetValidationReportIO()
				.readSnapshot(targetValidationReportFile.toPath(), maxFileSizeBytes);
		if (expectedFingerprint != null && !expectedFingerprint.equals(snapshot.fingerprint())) {
			throw new CommandException(
					"targetValidationReportFile fingerprint does not match expectedTargetValidationReportFingerprint.");
		}
		final var report = snapshot.report();
		final var plan = resolved.plan();
		if (!plan.getJobId().equals(report.jobId()) || !plan.getFingerprint().equals(report.planFingerprint())
				|| !plan.getTaskIds().equals(report.taskIds())
				|| !Objects.equals(resolved.provenance(), report.provenance()) || resolved.provenance() == null
				|| !resolved.provenance().configurationFingerprint().equals(report.configurationFingerprint())) {
			throw new CommandException("Target validation report does not match the resolved migration job.");
		}
		if (!Objects.equals(targetEnvironmentId, report.targetEnvironmentId())) {
			throw new CommandException("Target validation report does not match targetEnvironmentId.");
		}
		final Instant now = Instant.now();
		if (report.generatedAt().isAfter(now)) {
			throw new CommandException("Target validation report generatedAt is in the future.");
		}
		if (Duration.between(report.generatedAt(), now).compareTo(Duration.ofSeconds(maxAgeSeconds)) > 0) {
			throw new CommandException("Target validation report is older than maxTargetValidationAgeSeconds.");
		}
		return new ValidatedTargetReport(report, snapshot.fingerprint());
	}

	record ValidatedTargetReport(BulkMigrationTargetValidationReport report, String fingerprint) {
	}

	static void validateTargetDatabaseIdentity(final BulkMigrationTargetValidationReport report,
			final Connection connection) throws SQLException {
		if (report == null) {
			return;
		}
		final var metadata = connection.getMetaData();
		if (!report.databaseProductName().equals(metadata.getDatabaseProductName())
				|| !report.databaseProductVersion().equals(metadata.getDatabaseProductVersion())
				|| !Objects.equals(report.catalogName(), connection.getCatalog())
				|| !Objects.equals(report.schemaName(), connection.getSchema())) {
			throw new CommandException("Target validation report does not match the connected target database.");
		}
	}

	static BulkMigrationArtifactProvenance executionProvenance(final BulkMigrationArtifactProvenance provenance,
			final String targetValidationReportFingerprint) {
		if (targetValidationReportFingerprint == null || provenance == null) {
			return provenance;
		}
		return new BulkMigrationArtifactProvenance(provenance.configurationFingerprint(),
				provenance.assessmentReportFingerprint(), provenance.ddlVerificationReportFingerprint(),
				targetValidationReportFingerprint);
	}

	private static void validateArtifactFile(final File file, final String property) {
		if (file != null && !file.isFile()) {
			throw new CommandException(property + " must be an existing file.");
		}
	}

}
