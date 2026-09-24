/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.io.File;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.exceptions.CommandException;

import lombok.Getter;
import lombok.Setter;

/** Verifies a migration execution audit report without accessing a database. */
@Getter
@Setter
public class VerifyMigrationExecutionReportCommand extends AbstractCommand {
	private File reportFile;
	private String expectedReportFingerprint;
	private String expectedPlanFingerprint;
	private String expectedDatabaseConnectionFingerprint;
	private boolean requireSuccessful;
	private boolean requireAllSelectedCommitted;
	private MigrationExecutionReport report;

	@Override
	protected void doRun() {
		report = null;
		if (reportFile == null) {
			throw new CommandException("Migration execution report file is required.");
		}
		validateFingerprint("expectedReportFingerprint", expectedReportFingerprint);
		validateFingerprint("expectedPlanFingerprint", expectedPlanFingerprint);
		validateFingerprint("expectedDatabaseConnectionFingerprint", expectedDatabaseConnectionFingerprint);
		report = new MigrationExecutionReportIO().read(reportFile.toPath());
		if (expectedReportFingerprint != null
				&& !expectedReportFingerprint.equals(report.reportFingerprint())) {
			throw new CommandException("Migration execution report does not match expectedReportFingerprint: "
					+ reportFile);
		}
		if (expectedPlanFingerprint != null && !expectedPlanFingerprint.equals(report.planFingerprint())) {
			throw new CommandException("Migration execution report does not match expectedPlanFingerprint: "
					+ reportFile);
		}
		if (expectedDatabaseConnectionFingerprint != null
				&& (report.databaseIdentity() == null || !expectedDatabaseConnectionFingerprint
						.equals(report.databaseIdentity().connectionFingerprint()))) {
			throw new CommandException(
					"Migration execution report does not match expectedDatabaseConnectionFingerprint: " + reportFile);
		}
		if (requireSuccessful && !report.successful()) {
			throw new CommandException("Migration execution report records a failed migration: " + reportFile);
		}
		if (requireAllSelectedCommitted
				&& (!report.executionRequested() || !report.selectedVersions().equals(report.committedVersions()))) {
			throw new CommandException("Migration execution report does not show all selected versions committed: "
					+ reportFile);
		}
		info("Migration execution report verified: ", reportFile.getAbsolutePath());
	}

	private static void validateFingerprint(final String property, final String fingerprint) {
		if (fingerprint != null && !fingerprint.matches("sha256:[0-9a-f]{64}")) {
			throw new CommandException(property + " must be a lowercase SHA-256 value");
		}
	}
}
