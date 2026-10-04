/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.io.File;
import java.time.Instant;

import javax.sql.DataSource;

import com.sqlapp.data.db.command.AbstractDataSourceCommand;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.bulk.BulkMigrationTargetValidator;
import com.sqlapp.util.MessageDigests;

import lombok.Getter;
import lombok.Setter;

/** Validates a declarative bulk migration job against its live target without writing data. */
@Getter
@Setter
public class ValidateBulkMigrationTargetCommand extends AbstractDataSourceCommand {
	private File configurationFile;
	private String expectedConfigurationFingerprint;
	private File assessmentReportFile;
	private File ddlVerificationReportFile;
	private File reportFile;
	private String targetEnvironmentId;
	private DataSource sourceDataSource;
	private BulkMigrationTargetValidationResult result;

	@Override
	protected void doRun() {
		result = null;
		if (getDataSource() == null) {
			throw new CommandException("Bulk migration target data source is required.");
		}
		if (sourceDataSource == null) {
			throw new CommandException("Bulk migration source data source is required.");
		}
		if (configurationFile == null || !configurationFile.isFile()) {
			throw new CommandException("Bulk migration configuration file is required.");
		}
		if (targetEnvironmentId != null && targetEnvironmentId.isBlank()) {
			throw new CommandException("targetEnvironmentId must not be blank.");
		}
		validateArtifactFile(assessmentReportFile, "assessmentReportFile");
		validateArtifactFile(ddlVerificationReportFile, "ddlVerificationReportFile");
		final String configurationFingerprint = configurationFingerprint();
		executeNoTranAndClose(sourceDataSource, sourceConnection -> {
			final var resolution = new BulkMigrationJobConfigurationResolver().resolveJob(configurationFile,
					sourceConnection);
			validateApprovalArtifacts(resolution.provenance());
			final var plan = resolution.plan();
			executeNoTranAndClose(getDataSource(), targetConnection -> {
				BulkMigrationTargetValidator.validate(targetConnection, plan);
				result = new BulkMigrationTargetValidationResult(plan.getFingerprint(), configurationFingerprint,
						plan.getTasks().stream().map(task -> task.getTaskId()).toList());
				if (reportFile != null) {
					final var metadata = targetConnection.getMetaData();
					new BulkMigrationTargetValidationReportIO().write(reportFile.toPath(),
							new BulkMigrationTargetValidationReport(
									BulkMigrationTargetValidationReport.CURRENT_FORMAT_VERSION, Instant.now(),
									plan.getJobId(), plan.getFingerprint(), configurationFingerprint, result.taskIds(),
									resolution.provenance(), targetEnvironmentId, metadata.getDatabaseProductName(),
									metadata.getDatabaseProductVersion(), targetConnection.getCatalog(),
									targetConnection.getSchema()));
				}
			});
		});
		info("Bulk migration target validation completed: ", result.planFingerprint());
	}

	private static void validateArtifactFile(final File file, final String property) {
		if (file != null && !file.isFile()) {
			throw new CommandException(property + " must be an existing file.");
		}
	}

	private void validateApprovalArtifacts(final BulkMigrationArtifactProvenance provenance) {
		BulkMigrationArtifactProvenanceVerifier.verify(assessmentReportFile,
				provenance == null ? null : provenance.assessmentReportFingerprint(), "assessmentReportFile",
				"assessmentReportFingerprint");
		BulkMigrationArtifactProvenanceVerifier.verify(ddlVerificationReportFile,
				provenance == null ? null : provenance.ddlVerificationReportFingerprint(),
				"ddlVerificationReportFile", "ddlVerificationReportFingerprint");
	}

	private String configurationFingerprint() {
		if (expectedConfigurationFingerprint != null && !expectedConfigurationFingerprint.isBlank()
				&& !expectedConfigurationFingerprint.matches("sha256:[0-9a-f]{64}")) {
			throw new CommandException("expectedConfigurationFingerprint must be a lowercase SHA-256 value.");
		}
		try {
			final String actual = "sha256:" + MessageDigests.SHA256.checksumAsString(configurationFile);
			if (expectedConfigurationFingerprint != null && !expectedConfigurationFingerprint.isBlank()
					&& !expectedConfigurationFingerprint.equals(actual)) {
				throw new CommandException(
						"configurationFile fingerprint does not match expectedConfigurationFingerprint.");
			}
			return actual;
		} catch (final CommandException e) {
			throw e;
		} catch (final Exception e) {
			throw new CommandException("Could not fingerprint configurationFile: " + e.getMessage(), e);
		}
	}
}
