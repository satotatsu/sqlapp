/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import org.gradle.api.Action;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.migration.bulk.ExecuteBulkMigrationJobRepairCommand;
import com.sqlapp.gradle.plugins.extension.DataSourceExtension;

/** Re-verifies and executes an approved declarative bulk migration repair. */
@DisableCachingByDefault(because = "Executes mutations against an external database")
public abstract class ExecuteBulkMigrationJobRepairTask
		extends AbstractDbTask<ExecuteBulkMigrationJobRepairCommand> {
	public ExecuteBulkMigrationJobRepairTask() {
		setSourceDataSource(getProject().getObjects().newInstance(DataSourceExtension.class));
	}

	public void call(final Action<ExecuteBulkMigrationJobRepairTask> action) {
		action.execute(this);
	}

	@InputFile @PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getConfigurationFile();
	@Input @Optional
	public abstract Property<String> getExpectedConfigurationFingerprint();
	@InputFile @PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getApprovedRepairPlanFile();
	@Nested
	public abstract DataSourceExtension getSourceDataSource();
	public abstract void setSourceDataSource(DataSourceExtension value);
	public void sourceDataSource(final Action<DataSourceExtension> action) {
		action.execute(getSourceDataSource());
	}

	@Override
	protected void beforeRun(final ExecuteBulkMigrationJobRepairCommand command) {
		command.setConfigurationFile(getConfigurationFile().get().getAsFile());
		command.setApprovedRepairPlanFile(getApprovedRepairPlanFile().get().getAsFile());
		command.setSourceDataSource(getSourceDataSource().createDataSource());
		if (getExpectedConfigurationFingerprint().isPresent()) {
			command.setExpectedConfigurationFingerprint(getExpectedConfigurationFingerprint().get());
		}
	}

	@Override
	protected ExecuteBulkMigrationJobRepairCommand createCommand() {
		return new ExecuteBulkMigrationJobRepairCommand();
	}
}
