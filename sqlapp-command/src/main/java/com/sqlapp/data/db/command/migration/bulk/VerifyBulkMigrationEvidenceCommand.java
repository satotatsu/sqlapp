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
	private File outputFile;
	private boolean requireSuccessfulExecution = true;
	private boolean requireMatchingData = true;
	private boolean requireProvenance = true;
	private BulkMigrationOperationalReport operationalReport;
	private BulkMigrationVerificationReport verificationReport;
	private BulkMigrationEvidenceReport evidenceReport;

	@Override
	protected void doRun() {
		operationalReport = null;
		verificationReport = null;
		evidenceReport = null;
		requireFile(operationalReportFile, "operationalReportFile");
		requireFile(verificationReportFile, "verificationReportFile");
		optionalFile(configurationFile, "configurationFile");
		optionalFile(assessmentReportFile, "assessmentReportFile");
		optionalFile(ddlVerificationReportFile, "ddlVerificationReportFile");
		validateOutputFile();
		operationalReport = new BulkMigrationOperationalReportIO().read(operationalReportFile.toPath());
		verificationReport = new BulkMigrationVerificationReportIO().read(verificationReportFile.toPath());
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
		BulkMigrationArtifactProvenanceVerifier.verify(configurationFile,
				provenance == null ? null : provenance.configurationFingerprint(),
				"configurationFile", "configurationFingerprint");
		BulkMigrationArtifactProvenanceVerifier.verify(assessmentReportFile,
				provenance == null ? null : provenance.assessmentReportFingerprint(),
				"assessmentReportFile", "assessmentReportFingerprint");
		BulkMigrationArtifactProvenanceVerifier.verify(ddlVerificationReportFile,
				provenance == null ? null : provenance.ddlVerificationReportFingerprint(),
				"ddlVerificationReportFile", "ddlVerificationReportFingerprint");
		evidenceReport = evidenceReport(provenance);
		if (outputFile != null) {
			new BulkMigrationEvidenceReportIO().write(outputFile.toPath(), evidenceReport);
		}
		info("Bulk migration evidence verified: ", operationalReportFile.getAbsolutePath());
	}

	private BulkMigrationEvidenceReport evidenceReport(final BulkMigrationArtifactProvenance provenance) {
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
		return new BulkMigrationEvidenceReport(BulkMigrationEvidenceReport.CURRENT_FORMAT_VERSION, Instant.now(),
				operationalReport.jobId(), operationalReport.planFingerprint(), fingerprint(operationalReportFile),
				fingerprint(verificationReportFile),
				operationalReport.execution() == null ? null : operationalReport.execution().event(),
				verificationReport.match(),
				provenance, policies, artifacts);
	}

	private static String fingerprint(final File file) {
		return "sha256:" + com.sqlapp.util.MessageDigests.SHA256.checksumAsString(file);
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
