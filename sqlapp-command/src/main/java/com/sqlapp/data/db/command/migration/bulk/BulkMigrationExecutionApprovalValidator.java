/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.MessageDigests;

/** Shared approval validation for declarative migration and repair execution. */
final class BulkMigrationExecutionApprovalValidator {
	private BulkMigrationExecutionApprovalValidator() {
	}

	static void validateConfigurationFingerprint(final File configurationFile, final String expectedFingerprint) {
		if (expectedFingerprint == null || expectedFingerprint.isBlank()) {
			return;
		}
		if (configurationFile == null) {
			throw new CommandException("expectedConfigurationFingerprint requires configurationFile.");
		}
		if (!expectedFingerprint.matches("sha256:[0-9a-f]{64}")) {
			throw new CommandException("expectedConfigurationFingerprint must be a lowercase SHA-256 value.");
		}
		try {
			if (!expectedFingerprint.equals(fingerprint(configurationFile))) {
				throw new CommandException(
						"configurationFile fingerprint does not match expectedConfigurationFingerprint.");
			}
		} catch (final CommandException e) {
			throw e;
		} catch (final Exception e) {
			throw new CommandException("Could not fingerprint configurationFile: " + e.getMessage(), e);
		}
	}

	static void validateArtifactInputs(final File configurationFile, final File assessmentReportFile,
			final File ddlVerificationReportFile) {
		if ((assessmentReportFile != null || ddlVerificationReportFile != null) && configurationFile == null) {
			throw new CommandException("Approval artifact files require configurationFile.");
		}
		validateArtifactFile(assessmentReportFile, "assessmentReportFile");
		validateArtifactFile(ddlVerificationReportFile, "ddlVerificationReportFile");
	}

	static void validateTargetInputs(final File configurationFile, final File targetValidationReportFile,
			final String expectedFingerprint, final Long maxAgeSeconds, final String targetEnvironmentId) {
		if (targetValidationReportFile == null) {
			if (expectedFingerprint != null || maxAgeSeconds != null || targetEnvironmentId != null) {
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
		if (targetEnvironmentId != null && targetEnvironmentId.isBlank()) {
			throw new CommandException("targetEnvironmentId must not be blank.");
		}
		if (expectedFingerprint != null && !expectedFingerprint.matches("sha256:[0-9a-f]{64}")) {
			throw new CommandException("expectedTargetValidationReportFingerprint must be a lowercase SHA-256 value.");
		}
		if (expectedFingerprint != null) {
			final String actual = fingerprint(targetValidationReportFile);
			if (!expectedFingerprint.equals(actual)) {
				throw new CommandException(
						"targetValidationReportFile fingerprint does not match expectedTargetValidationReportFingerprint.");
			}
		}
	}

	static void validateArtifacts(final BulkMigrationArtifactProvenance provenance, final File assessmentReportFile,
			final File ddlVerificationReportFile) {
		BulkMigrationArtifactProvenanceVerifier.verify(assessmentReportFile,
				provenance == null ? null : provenance.assessmentReportFingerprint(), "assessmentReportFile",
				"assessmentReportFingerprint");
		BulkMigrationArtifactProvenanceVerifier.verify(ddlVerificationReportFile,
				provenance == null ? null : provenance.ddlVerificationReportFingerprint(),
				"ddlVerificationReportFile", "ddlVerificationReportFingerprint");
	}

	static BulkMigrationTargetValidationReport validateTargetReport(final File targetValidationReportFile,
			final String targetEnvironmentId, final long maxAgeSeconds,
			final BulkMigrationJobConfigurationResolver.Resolution resolved) {
		if (targetValidationReportFile == null) {
			return null;
		}
		final var report = new BulkMigrationTargetValidationReportIO().read(targetValidationReportFile.toPath());
		final var plan = resolved.plan();
		if (!plan.getJobId().equals(report.jobId()) || !plan.getFingerprint().equals(report.planFingerprint())
				|| !plan.getTaskIds().equals(report.taskIds()) || !Objects.equals(resolved.provenance(), report.provenance())
				|| resolved.provenance() == null || !resolved.provenance().configurationFingerprint()
						.equals(report.configurationFingerprint())) {
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
		return report;
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
			final File targetValidationReportFile) {
		if (targetValidationReportFile == null || provenance == null) {
			return provenance;
		}
		return new BulkMigrationArtifactProvenance(provenance.configurationFingerprint(),
				provenance.assessmentReportFingerprint(), provenance.ddlVerificationReportFingerprint(),
				fingerprint(targetValidationReportFile));
	}

	private static void validateArtifactFile(final File file, final String property) {
		if (file != null && !file.isFile()) {
			throw new CommandException(property + " must be an existing file.");
		}
	}

	private static String fingerprint(final File file) {
		return "sha256:" + MessageDigests.SHA256.checksumAsString(file);
	}
}
