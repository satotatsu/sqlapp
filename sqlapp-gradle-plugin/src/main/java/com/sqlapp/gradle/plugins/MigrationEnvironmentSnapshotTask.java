/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.migration.schema.MigrationCommand;
import com.sqlapp.data.db.command.migration.environment.MigrationEnvironmentSnapshotCommand;

/** Captures the migration state of one configured database. */
@DisableCachingByDefault(because = "Migration state is read from an external database")
public abstract class MigrationEnvironmentSnapshotTask extends MigrationTask {
	public MigrationEnvironmentSnapshotTask() {
		getOutputs().upToDateWhen(task -> false);
	}

	@Input
	public abstract Property<String> getEnvironmentId();

	@OutputFile
	public abstract RegularFileProperty getOutputFile();

	@Override
	protected MigrationCommand createCommand() {
		return new MigrationEnvironmentSnapshotCommand();
	}

	@Override
	protected void initializeCommand(final MigrationCommand command) {
		super.initializeCommand(command);
		final var snapshot = (MigrationEnvironmentSnapshotCommand) command;
		snapshot.setEnvironmentId(getEnvironmentId().get());
		snapshot.setOutputFile(getOutputFile().get().getAsFile());
	}
}
