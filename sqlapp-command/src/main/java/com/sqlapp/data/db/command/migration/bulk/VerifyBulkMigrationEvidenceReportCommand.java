/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.io.File;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.MessageDigests;

import lombok.Getter;
import lombok.Setter;

/** Revalidates a saved bulk migration audit artifact and its source reports. */
@Getter
@Setter
public class VerifyBulkMigrationEvidenceReportCommand extends AbstractCommand {
	private File evidenceReportFile;
	private File operationalReportFile;
	private File verificationReportFile;
	private File configurationFile;
	private File assessmentReportFile;
	private File ddlVerificationReportFile;
	private String expectedEvidenceReportFingerprint;
	private String expectedPlanFingerprint;
	private String expectedConfigurationFingerprint;
	private Long maxEvidenceAgeSeconds;
	private BulkMigrationEvidenceReport report;

	@Override
	protected void doRun() {
		report = null;
		requireFile(evidenceReportFile, "evidenceReportFile");
		requireFile(operationalReportFile, "operationalReportFile");
		requireFile(verificationReportFile, "verificationReportFile");
		optionalFile(configurationFile, "configurationFile");
		optionalFile(assessmentReportFile, "assessmentReportFile");
		optionalFile(ddlVerificationReportFile, "ddlVerificationReportFile");
		if (expectedEvidenceReportFingerprint != null) {
			if (!expectedEvidenceReportFingerprint.matches("sha256:[0-9a-f]{64}")) {
				throw new CommandException("expectedEvidenceReportFingerprint must be a lowercase SHA-256 value.");
			}
			final String actual = "sha256:" + MessageDigests.SHA256.checksumAsString(evidenceReportFile);
			if (!expectedEvidenceReportFingerprint.equals(actual)) {
				throw new CommandException(
						"evidenceReportFile fingerprint does not match expectedEvidenceReportFingerprint.");
			}
		}
		if (expectedPlanFingerprint != null && expectedPlanFingerprint.isBlank()) {
			throw new CommandException("expectedPlanFingerprint must not be blank.");
		}
		if (expectedConfigurationFingerprint != null
				&& !expectedConfigurationFingerprint.matches("sha256:[0-9a-f]{64}")) {
			throw new CommandException("expectedConfigurationFingerprint must be a lowercase SHA-256 value.");
		}
		if (maxEvidenceAgeSeconds != null && maxEvidenceAgeSeconds <= 0) {
			throw new CommandException("maxEvidenceAgeSeconds must be greater than zero.");
		}
		report = new BulkMigrationEvidenceReportIO().read(evidenceReportFile.toPath(),
				operationalReportFile.toPath(), verificationReportFile.toPath());
		verifySourceArtifact(configurationFile, BulkMigrationEvidenceReport.ARTIFACT_CONFIGURATION,
				report.provenance() == null ? null : report.provenance().configurationFingerprint(),
				"configurationFile", "configurationFingerprint");
		verifySourceArtifact(assessmentReportFile, BulkMigrationEvidenceReport.ARTIFACT_ASSESSMENT_REPORT,
				report.provenance() == null ? null : report.provenance().assessmentReportFingerprint(),
				"assessmentReportFile", "assessmentReportFingerprint");
		verifySourceArtifact(ddlVerificationReportFile,
				BulkMigrationEvidenceReport.ARTIFACT_DDL_VERIFICATION_REPORT,
				report.provenance() == null ? null : report.provenance().ddlVerificationReportFingerprint(),
				"ddlVerificationReportFile", "ddlVerificationReportFingerprint");
		if (expectedPlanFingerprint != null && !expectedPlanFingerprint.equals(report.planFingerprint())) {
			throw new CommandException("Evidence report does not match expectedPlanFingerprint.");
		}
		if (expectedConfigurationFingerprint != null && (report.provenance() == null
				|| !expectedConfigurationFingerprint.equals(report.provenance().configurationFingerprint()))) {
			throw new CommandException("Evidence report does not match expectedConfigurationFingerprint.");
		}
		if (maxEvidenceAgeSeconds != null) {
			final java.time.Instant now = java.time.Instant.now();
			if (report.generatedAt().isAfter(now)) {
				throw new CommandException("Evidence report generatedAt is in the future; check clock synchronization.");
			}
			try {
				if (report.generatedAt().plusSeconds(maxEvidenceAgeSeconds).isBefore(now)) {
					throw new CommandException("Evidence report has expired.");
				}
			} catch (final java.time.DateTimeException | ArithmeticException e) {
				throw new CommandException("maxEvidenceAgeSeconds is outside the supported time range.", e);
			}
		}
		info("Bulk migration evidence report verified: ", evidenceReportFile.getAbsolutePath());
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

	private void verifySourceArtifact(final File file, final String artifact, final String expectedFingerprint,
			final String fileProperty, final String provenanceProperty) {
		if (file == null) {
			return;
		}
		if (!report.verifiedArtifacts().contains(artifact)) {
			throw new CommandException(fileProperty + " was not verified by the saved evidence report.");
		}
		BulkMigrationArtifactProvenanceVerifier.verify(file, expectedFingerprint, fileProperty, provenanceProperty);
	}
}
