/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.snapshot;


import java.io.File;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.exceptions.CommandException;

import lombok.Getter;
import lombok.Setter;

/**
 * Generates a connection-free review artifact from snapshot YAML and Schema
 * XML.
 */
@Getter
@Setter
public class GenerateMigrationSnapshotApprovalReportCommand extends AbstractCommand {
	private File configurationFile;
	private File targetFile;
	private MigrationSnapshotApprovalReport report;

	@Override
	protected void doRun() {
		report = null;
		if (targetFile == null) {
			throw new CommandException("Migration snapshot approval report target file is required.");
		}
		final var resolution = new MigrationSnapshotConfigurationResolver().resolveForApproval(configurationFile);
		final var io = new MigrationSnapshotApprovalReportIO();
		report = io.fromResolution(resolution);
		io.write(targetFile.toPath(), report);
		info("Migration snapshot approval report: ", targetFile.getAbsolutePath());
	}
}
