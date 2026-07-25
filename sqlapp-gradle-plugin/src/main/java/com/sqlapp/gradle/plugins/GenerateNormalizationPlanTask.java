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
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.normalization.GenerateNormalizationPlanCommand;

@DisableCachingByDefault
public abstract class GenerateNormalizationPlanTask
		extends AbstractTask<GenerateNormalizationPlanCommand> {

	public GenerateNormalizationPlanTask() {
		getMinimumColumnCount().convention(2);
		getVariableCharacterMinimumLength().convention(20L);
		getPreviewSchemaEnabled().convention(true);
	}

	public void call(Action<GenerateNormalizationPlanTask> action) {
		action.execute(this);
	}

	@InputFile
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getTargetFile();

	@InputFile
	@Optional
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getMigrationMappingFile();

	@OutputDirectory
	public abstract DirectoryProperty getOutputDirectory();

	@Input
	public abstract Property<Integer> getMinimumColumnCount();

	@Input
	public abstract Property<Long> getVariableCharacterMinimumLength();

	@Input
	public abstract Property<Boolean> getPreviewSchemaEnabled();

	@Override
	protected void beforeRun(GenerateNormalizationPlanCommand command) {
		command.setTargetFile(getTargetFile().get().getAsFile());
		if (getMigrationMappingFile().isPresent()) {
			command.setMigrationMappingFile(getMigrationMappingFile().get().getAsFile());
		}
		command.setOutputDirectory(getOutputDirectory().get().getAsFile());
		command.setMinimumColumnCount(getMinimumColumnCount().get());
		command.setVariableCharacterMinimumLength(
				getVariableCharacterMinimumLength().get());
		command.setPreviewSchemaEnabled(getPreviewSchemaEnabled().get());
	}

	@Override
	protected GenerateNormalizationPlanCommand createCommand() {
		return new GenerateNormalizationPlanCommand();
	}
}
