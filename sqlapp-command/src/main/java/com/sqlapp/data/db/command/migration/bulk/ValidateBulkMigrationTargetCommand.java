/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.io.File;
import java.time.Instant;

import javax.sql.DataSource;

import com.sqlapp.data.db.command.AbstractDataSourceCommand;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.bulk.BulkMigrationTargetValidator;

import lombok.Getter;
import lombok.Setter;

/**
 * Validates a declarative bulk migration job against its live target without
 * writing data.
 */
@Getter
@Setter
public class ValidateBulkMigrationTargetCommand extends AbstractDataSourceCommand {
	private File configurationFile;
	private String expectedConfigurationFingerprint;
	private Long maxConfigurationFileSizeBytes;
	private Long maxSchemaFileSizeBytes;
	private File assessmentReportFile;
	private File ddlVerificationReportFile;
	private Long maxApprovalArtifactFileSizeBytes;
	private File reportFile;
	private Long maxTargetValidationReportFileSizeBytes;
	private String targetEnvironmentId;
	private DataSource sourceDataSource;
	private BulkMigrationTargetValidationResult result;
	private String targetValidationReportFingerprint;

	@Override
	protected void doRun() {
		result = null;
		targetValidationReportFingerprint = null;
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
		if (maxTargetValidationReportFileSizeBytes != null) {
			if (reportFile == null) {
				throw new CommandException("maxTargetValidationReportFileSizeBytes requires reportFile.");
			}
			if (maxTargetValidationReportFileSizeBytes <= 0) {
				throw new CommandException("maxTargetValidationReportFileSizeBytes must be greater than zero.");
			}
		}
		validateArtifactFile(assessmentReportFile, "assessmentReportFile");
		validateArtifactFile(ddlVerificationReportFile, "ddlVerificationReportFile");
		if (maxApprovalArtifactFileSizeBytes != null && maxApprovalArtifactFileSizeBytes <= 0) {
			throw new CommandException("maxApprovalArtifactFileSizeBytes must be greater than zero.");
		}
		BulkMigrationExecutionApprovalValidator.validateConfigurationInputs(configurationFile,
				expectedConfigurationFingerprint, maxConfigurationFileSizeBytes);
		executeNoTranAndClose(sourceDataSource, sourceConnection -> {
			final var resolution = new BulkMigrationJobConfigurationResolver().resolveJob(configurationFile,
					sourceConnection, expectedConfigurationFingerprint, maxConfigurationFileSizeBytes,
					maxSchemaFileSizeBytes);
			validateApprovalArtifacts(resolution.provenance());
			final var plan = resolution.plan();
			final String configurationFingerprint = resolution.provenance().configurationFingerprint();
			executeNoTranAndClose(getDataSource(), targetConnection -> {
				BulkMigrationTargetValidator.validate(targetConnection, plan);
				final var validated = new BulkMigrationTargetValidationResult(plan.getFingerprint(),
						configurationFingerprint, plan.getTasks().stream().map(task -> task.getTaskId()).toList());
				if (reportFile != null) {
					final var metadata = targetConnection.getMetaData();
					final var reportIO = new BulkMigrationTargetValidationReportIO();
					final var snapshot = reportIO.writeSnapshot(reportFile.toPath(),
							new BulkMigrationTargetValidationReport(
									BulkMigrationTargetValidationReport.CURRENT_FORMAT_VERSION, Instant.now(),
									plan.getJobId(), plan.getFingerprint(), configurationFingerprint,
									validated.taskIds(), resolution.provenance(), targetEnvironmentId,
									metadata.getDatabaseProductName(), metadata.getDatabaseProductVersion(),
									targetConnection.getCatalog(), targetConnection.getSchema()),
							maxTargetValidationReportFileSizeBytes);
					targetValidationReportFingerprint = snapshot.fingerprint();
				}
				result = validated;
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
				"assessmentReportFingerprint", maxApprovalArtifactFileSizeBytes);
		BulkMigrationArtifactProvenanceVerifier.verify(ddlVerificationReportFile,
				provenance == null ? null : provenance.ddlVerificationReportFingerprint(), "ddlVerificationReportFile",
				"ddlVerificationReportFingerprint", maxApprovalArtifactFileSizeBytes);
	}

}
