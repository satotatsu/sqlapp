/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.io.File;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.bulk.BulkMigrationJobRepairPlan;

import lombok.Getter;
import lombok.Setter;

/** Writes a review-only JSON snapshot of a prepared job repair plan. */
@Getter
@Setter
public class GenerateBulkMigrationJobRepairPlanReportCommand extends AbstractCommand {
	private BulkMigrationJobRepairPlan plan;
	private File targetFile;
	private Long maxRepairPlanReportFileSizeBytes;
	private BulkMigrationJobRepairPlanReport report;
	private String reportFingerprint;

	@Override
	protected void doRun() {
		report = null;
		reportFingerprint = null;
		if (plan == null) {
			throw new CommandException("Bulk migration job repair plan is required.");
		}
		if (targetFile == null) {
			throw new CommandException("Bulk migration job repair plan report target file is required.");
		}
		if (maxRepairPlanReportFileSizeBytes != null && maxRepairPlanReportFileSizeBytes <= 0) {
			throw new CommandException("maxRepairPlanReportFileSizeBytes must be greater than zero.");
		}
		final var io = new BulkMigrationJobRepairPlanReportIO();
		final var snapshot = io.writeSnapshot(targetFile.toPath(), io.fromPlan(plan), maxRepairPlanReportFileSizeBytes);
		report = snapshot.report();
		reportFingerprint = snapshot.fingerprint();
		info("Bulk migration job repair plan report: ", targetFile.getAbsolutePath());
	}
}
