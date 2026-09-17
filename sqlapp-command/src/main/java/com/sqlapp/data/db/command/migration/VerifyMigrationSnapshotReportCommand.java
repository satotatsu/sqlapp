/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.io.File;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.exceptions.CommandException;

import lombok.Getter;
import lombok.Setter;

/** Verifies that a completed SCD2 report is bound to an exact approval artifact. */
@Getter
@Setter
public class VerifyMigrationSnapshotReportCommand extends AbstractCommand {
	private File reportFile;
	private File approvalFile;
	private File configurationFile;
	private MigrationSnapshotExecutionReport report;
	private MigrationSnapshotApprovalReport approval;

	@Override
	protected void doRun() {
		report = null;
		approval = null;
		if (reportFile == null) throw new CommandException("Migration snapshot report file is required.");
		if (approvalFile == null) throw new CommandException("Migration snapshot approval file is required.");
		final var io = new MigrationSnapshotExecutionReportIO();
		report = io.read(reportFile.toPath());
		approval = io.verifyApproval(report, approvalFile.toPath());
		if (configurationFile != null) {
			io.verifyConfiguration(report,
					new MigrationSnapshotConfigurationResolver().resolveForApproval(configurationFile));
		}
		info("Migration snapshot approval evidence verified: ", reportFile.getAbsolutePath());
	}
}
