/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import org.gradle.api.Action;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.migration.GenerateBulkMigrationOperationalReportCommand;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlan;
import com.sqlapp.jdbc.bulk.BulkMigrationJobStatus;
import com.sqlapp.jdbc.bulk.BulkMigrationMaintenanceState;
import com.sqlapp.jdbc.bulk.BulkMigrationProgressSnapshot;

/** Writes a JSON operational snapshot for a programmatically assembled plan. */
@DisableCachingByDefault(because = "Migration status is read from mutable external stores")
public abstract class GenerateBulkMigrationOperationalReportTask
		extends AbstractTask<GenerateBulkMigrationOperationalReportCommand> {

	public void call(Action<GenerateBulkMigrationOperationalReportTask> action) {
		action.execute(this);
	}

	@Internal
	public abstract Property<BulkMigrationJobPlan> getPlan();

	@Internal
	public abstract Property<BulkMigrationJobStatus> getStatus();

	@Internal
	public abstract Property<BulkMigrationMaintenanceState> getMaintenanceState();

	@Internal
	public abstract Property<BulkMigrationProgressSnapshot> getProgress();

	@OutputFile
	public abstract RegularFileProperty getTargetFile();

	@Override
	protected void beforeRun(GenerateBulkMigrationOperationalReportCommand command) {
		command.setPlan(getPlan().get());
		command.setStatus(getStatus().get());
		command.setTargetFile(getTargetFile().get().getAsFile());
		if (getMaintenanceState().isPresent()) {
			command.setMaintenanceState(getMaintenanceState().get());
		}
		if (getProgress().isPresent()) {
			command.setProgress(getProgress().get());
		}
	}

	@Override
	protected GenerateBulkMigrationOperationalReportCommand createCommand() {
		return new GenerateBulkMigrationOperationalReportCommand();
	}
}
