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

import com.sqlapp.data.db.command.normalization.ColumnRuleTransformCommand;
import com.sqlapp.gradle.plugins.properties.OutputDirectoryTaskProperty;
import com.sqlapp.gradle.plugins.properties.TargetFileTaskProperty;

/**
 * Gradle task for applying YAML column transformation rules to schema XML.
 */
@DisableCachingByDefault
public abstract class ColumnRuleTransformTask extends AbstractTask<ColumnRuleTransformCommand>
		implements TargetFileTaskProperty, OutputDirectoryTaskProperty {

	public void call(Action<ColumnRuleTransformTask> action) {
		action.execute(this);
	}

	@InputFile
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getRulesFile();

	@OutputDirectory
	@Optional
	public abstract DirectoryProperty getTransformLogDirectory();

	@Input
	@Optional
	public abstract Property<String> getTransformLogFileName();

	@Override
	protected void beforeRun(ColumnRuleTransformCommand command) {
		command.setRulesFile(getRulesFile().get().getAsFile());
		if (getTransformLogDirectory().isPresent()) {
			command.setTransformLogDirectory(getTransformLogDirectory().get().getAsFile());
		}
		if (getTransformLogFileName().isPresent()) {
			command.setTransformLogFileName(getTransformLogFileName().get());
		}
	}

	@Override
	protected ColumnRuleTransformCommand createCommand() {
		return new ColumnRuleTransformCommand();
	}
}
