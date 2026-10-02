/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.io.File;
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
	private boolean requireSuccessfulExecution = true;
	private boolean requireMatchingData = true;
	private boolean requireProvenance = true;
	private BulkMigrationOperationalReport operationalReport;
	private BulkMigrationVerificationReport verificationReport;

	@Override
	protected void doRun() {
		operationalReport = null;
		verificationReport = null;
		requireFile(operationalReportFile, "operationalReportFile");
		requireFile(verificationReportFile, "verificationReportFile");
		optionalFile(configurationFile, "configurationFile");
		optionalFile(assessmentReportFile, "assessmentReportFile");
		optionalFile(ddlVerificationReportFile, "ddlVerificationReportFile");
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
		info("Bulk migration evidence verified: ", operationalReportFile.getAbsolutePath());
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
