/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.io.File;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlan;
import com.sqlapp.jdbc.bulk.BulkMigrationJobStatus;
import com.sqlapp.jdbc.bulk.BulkMigrationMaintenanceState;
import com.sqlapp.jdbc.bulk.BulkMigrationProgressSnapshot;

import lombok.Getter;
import lombok.Setter;

/** Writes one read-only JSON operational snapshot for a bulk migration plan. */
@Getter
@Setter
public class GenerateBulkMigrationOperationalReportCommand extends AbstractCommand {
	private BulkMigrationJobPlan plan;
	private BulkMigrationJobStatus status;
	private BulkMigrationMaintenanceState maintenanceState;
	private BulkMigrationProgressSnapshot progress;
	private File targetFile;

	@Override
	protected void doRun() {
		if (plan == null) {
			throw new CommandException("Bulk migration plan is required.");
		}
		if (status == null) {
			throw new CommandException("Bulk migration status is required.");
		}
		if (targetFile == null) {
			throw new CommandException("Bulk migration report target file is required.");
		}
		final var report = new BulkMigrationOperationalReportBuilder().build(plan, status,
				maintenanceState, progress);
		new BulkMigrationOperationalReportIO().write(targetFile.toPath(), report);
		info("Bulk migration operational report: ", targetFile.getAbsolutePath());
	}
}
