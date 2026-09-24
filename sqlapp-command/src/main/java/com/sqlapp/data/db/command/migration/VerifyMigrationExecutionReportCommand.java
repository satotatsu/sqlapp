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
	private boolean requireSuccessful;
	private MigrationExecutionReport report;

	@Override
	protected void doRun() {
		report = null;
		if (reportFile == null) {
			throw new CommandException("Migration execution report file is required.");
		}
		if (expectedReportFingerprint != null
				&& !expectedReportFingerprint.matches("sha256:[0-9a-f]{64}")) {
			throw new CommandException("expectedReportFingerprint must be a lowercase SHA-256 value");
		}
		report = new MigrationExecutionReportIO().read(reportFile.toPath());
		if (expectedReportFingerprint != null
				&& !expectedReportFingerprint.equals(report.reportFingerprint())) {
			throw new CommandException("Migration execution report does not match expectedReportFingerprint: "
					+ reportFile);
		}
		if (requireSuccessful && !report.successful()) {
			throw new CommandException("Migration execution report records a failed migration: " + reportFile);
		}
		info("Migration execution report verified: ", reportFile.getAbsolutePath());
	}
}
