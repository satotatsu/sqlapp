/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.Internal;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.migration.logic.ImportLogicMigrationResponseCommand;

/**
 * Imports a converter response into a logic migration request work directory.
 */
@DisableCachingByDefault(because = "The import publishes evidence once and refuses to overwrite it")
public abstract class ImportLogicMigrationResponseTask extends AbstractTask<ImportLogicMigrationResponseCommand> {
	public ImportLogicMigrationResponseTask() {
		getOutputs().upToDateWhen(task -> false);
	}

	/**
	 * Work directory written by generateLogicMigrationRequest. Not declared as an
	 * input or output because the command verifies the files itself and creates
	 * {@code import/} only when it does not exist.
	 */
	@Internal
	public abstract DirectoryProperty getWorkDir();

	@Override
	protected ImportLogicMigrationResponseCommand createCommand() {
		return new ImportLogicMigrationResponseCommand();
	}

	@Override
	protected void beforeRun(final ImportLogicMigrationResponseCommand command) {
		command.setWorkDir(getWorkDir().get().getAsFile());
	}
}
