/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.migration.logic.GenerateLogicMigrationRequestCommand;

/**
 * Writes a trigger conversion request from saved source and target Schema XML.
 */
@DisableCachingByDefault(because = "Each request gets a new ID and work directory")
public abstract class GenerateLogicMigrationRequestTask extends AbstractTask<GenerateLogicMigrationRequestCommand> {
	public GenerateLogicMigrationRequestTask() {
		getOutputs().upToDateWhen(task -> false);
	}

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getSourceSchemaFile();

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getTargetSchemaFile();

	/**
	 * New directory to publish. Not declared as an output because Gradle would
	 * create it before the command checks that it does not exist.
	 */
	@Internal
	public abstract DirectoryProperty getWorkDir();

	@Override
	protected GenerateLogicMigrationRequestCommand createCommand() {
		return new GenerateLogicMigrationRequestCommand();
	}

	@Override
	protected void beforeRun(final GenerateLogicMigrationRequestCommand command) {
		command.setSourceSchemaFile(getSourceSchemaFile().get().getAsFile());
		command.setTargetSchemaFile(getTargetSchemaFile().get().getAsFile());
		command.setWorkDir(getWorkDir().get().getAsFile());
	}
}
