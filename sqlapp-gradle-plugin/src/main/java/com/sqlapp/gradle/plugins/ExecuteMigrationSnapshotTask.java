/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import org.gradle.api.Action;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.migration.ExecuteMigrationSnapshotCommand;
import com.sqlapp.gradle.plugins.extension.DataSourceExtension;

/** Executes one YAML-defined atomic SCD2 snapshot. */
@DisableCachingByDefault(because = "Executes mutations against an external database")
public abstract class ExecuteMigrationSnapshotTask extends AbstractDbTask<ExecuteMigrationSnapshotCommand> {
	public ExecuteMigrationSnapshotTask() {
		setSourceDataSource(getProject().getObjects().newInstance(DataSourceExtension.class));
	}

	public void call(final Action<ExecuteMigrationSnapshotTask> action) {
		action.execute(this);
	}

	@InputFile
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getConfigurationFile();

	@Nested
	public abstract DataSourceExtension getSourceDataSource();

	public abstract void setSourceDataSource(DataSourceExtension value);

	public void sourceDataSource(final Action<DataSourceExtension> action) {
		action.execute(getSourceDataSource());
	}

	@Override
	protected void beforeRun(final ExecuteMigrationSnapshotCommand command) {
		command.setConfigurationFile(getConfigurationFile().get().getAsFile());
		command.setSourceDataSource(getSourceDataSource().createDataSource());
	}

	@Override
	protected ExecuteMigrationSnapshotCommand createCommand() {
		return new ExecuteMigrationSnapshotCommand();
	}
}
