/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import org.gradle.api.Action;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.migration.GenerateBulkMigrationJobRepairPlanReportCommand;
import com.sqlapp.jdbc.bulk.BulkMigrationJobRepairPlan;

/** Writes a review-only JSON snapshot for a programmatically assembled repair plan. */
@DisableCachingByDefault(because = "The programmatic repair plan is not a serializable Gradle input")
public abstract class GenerateBulkMigrationJobRepairPlanReportTask
		extends AbstractTask<GenerateBulkMigrationJobRepairPlanReportCommand> {

	public void call(Action<GenerateBulkMigrationJobRepairPlanReportTask> action) {
		action.execute(this);
	}

	@Internal
	public abstract Property<BulkMigrationJobRepairPlan> getPlan();

	@OutputFile
	public abstract RegularFileProperty getTargetFile();

	@Override
	protected void beforeRun(GenerateBulkMigrationJobRepairPlanReportCommand command) {
		command.setPlan(getPlan().get());
		command.setTargetFile(getTargetFile().get().getAsFile());
	}

	@Override
	protected GenerateBulkMigrationJobRepairPlanReportCommand createCommand() {
		return new GenerateBulkMigrationJobRepairPlanReportCommand();
	}
}
