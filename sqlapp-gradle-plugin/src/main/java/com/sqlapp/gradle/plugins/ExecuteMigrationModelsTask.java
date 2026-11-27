/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import org.gradle.api.Action;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.migration.model.ExecuteMigrationModelsCommand;

/** Tests and builds YAML-defined SELECT models inside the connected engine. */
@DisableCachingByDefault(because = "Executes mutations against a database")
public abstract class ExecuteMigrationModelsTask extends AbstractDbTask<ExecuteMigrationModelsCommand> {
	public ExecuteMigrationModelsTask() {
		getModifiedOnly().convention(false);
		getUnitTestsEnabled().convention(true);
		getTestOnly().convention(false);
	}

	public void call(final Action<ExecuteMigrationModelsTask> action) {
		action.execute(this);
	}

	@InputFile
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getConfigurationFile();

	/** Read as the baseline of modifiedOnly and rewritten after a complete build. */
	@Internal
	public abstract RegularFileProperty getStateFile();

	@Input
	public abstract Property<Boolean> getModifiedOnly();

	@Input
	public abstract Property<Boolean> getUnitTestsEnabled();

	@Input
	public abstract Property<Boolean> getTestOnly();

	@Override
	protected void beforeRun(final ExecuteMigrationModelsCommand command) {
		command.setConfigurationFile(getConfigurationFile().get().getAsFile());
		command.setStateFile(getStateFile().isPresent() ? getStateFile().get().getAsFile() : null);
		command.setModifiedOnly(getModifiedOnly().get());
		command.setUnitTestsEnabled(getUnitTestsEnabled().get());
		command.setTestOnly(getTestOnly().get());
	}

	@Override
	protected ExecuteMigrationModelsCommand createCommand() {
		return new ExecuteMigrationModelsCommand();
	}
}
