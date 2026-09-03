/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

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

	@Override
	protected void doRun() {
		if (plan == null) {
			throw new CommandException("Bulk migration job repair plan is required.");
		}
		if (targetFile == null) {
			throw new CommandException(
					"Bulk migration job repair plan report target file is required.");
		}
		new BulkMigrationJobRepairPlanReportIO().write(targetFile.toPath(), plan);
		info("Bulk migration job repair plan report: ", targetFile.getAbsolutePath());
	}
}
