/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-gradle-plugin.
 */
package com.sqlapp.gradle.plugins;

import org.gradle.api.Action;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.migration.GeneratePliCsvExtractorCommand;

/**
 * Gradle task for generating PL/I CSV extraction artifacts.
 */
@DisableCachingByDefault
public abstract class GeneratePliCsvExtractorTask extends AbstractTask<GeneratePliCsvExtractorCommand> {

	public GeneratePliCsvExtractorTask() {
		getProgramName().convention("SQLAPEXT");
	}

	public void call(Action<GeneratePliCsvExtractorTask> action) {
		action.execute(this);
	}

	@InputFile
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getContractFile();

	@OutputDirectory
	public abstract DirectoryProperty getOutputDirectory();

	@Input
	public abstract Property<String> getProgramName();

	@Override
	protected void beforeRun(GeneratePliCsvExtractorCommand command) {
		command.setContractFile(getContractFile().get().getAsFile());
		command.setOutputDirectory(getOutputDirectory().get().getAsFile());
		command.setProgramName(getProgramName().get());
	}

	@Override
	protected GeneratePliCsvExtractorCommand createCommand() {
		return new GeneratePliCsvExtractorCommand();
	}
}
