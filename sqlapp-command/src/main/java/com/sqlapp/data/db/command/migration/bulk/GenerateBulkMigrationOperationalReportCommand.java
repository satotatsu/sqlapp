/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;


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
	private Long maxOperationalReportFileSizeBytes;
	private BulkMigrationOperationalReport report;
	private String reportFingerprint;

	@Override
	protected void doRun() {
		report = null;
		reportFingerprint = null;
		if (plan == null) {
			throw new CommandException("Bulk migration plan is required.");
		}
		if (status == null) {
			throw new CommandException("Bulk migration status is required.");
		}
		if (targetFile == null) {
			throw new CommandException("Bulk migration report target file is required.");
		}
		if (maxOperationalReportFileSizeBytes != null && maxOperationalReportFileSizeBytes <= 0) {
			throw new CommandException("maxOperationalReportFileSizeBytes must be greater than zero.");
		}
		final var requested = new BulkMigrationOperationalReportBuilder().build(plan, status, maintenanceState, progress);
		final var snapshot = new BulkMigrationOperationalReportIO().writeSnapshot(targetFile.toPath(), requested,
				maxOperationalReportFileSizeBytes);
		report = snapshot.report();
		reportFingerprint = snapshot.fingerprint();
		info("Bulk migration operational report: ", targetFile.getAbsolutePath());
	}
}
