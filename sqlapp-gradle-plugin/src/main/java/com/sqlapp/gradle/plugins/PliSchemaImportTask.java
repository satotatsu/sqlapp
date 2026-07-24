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

import com.sqlapp.data.db.command.host.pli.PliSchemaImportCommand;
import com.sqlapp.gradle.plugins.properties.OutputDirectoryTaskProperty;
import com.sqlapp.gradle.plugins.properties.TargetFileTaskProperty;

/**
 * Gradle task for importing PL/I structures as schema XML.
 */
@DisableCachingByDefault
public abstract class PliSchemaImportTask extends AbstractTask<PliSchemaImportCommand>
		implements TargetFileTaskProperty, OutputDirectoryTaskProperty {

	public PliSchemaImportTask() {
		getEncoding().convention("UTF-8");
	}

	public void call(Action<PliSchemaImportTask> action) {
		action.execute(this);
	}

	@InputFile
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getConfigurationFile();

	@Input
	public abstract Property<String> getEncoding();

	@Input
	@Optional
	public abstract Property<String> getOutputFileName();

	@OutputDirectory
	@Optional
	public abstract DirectoryProperty getImportLogDirectory();

	@Input
	@Optional
	public abstract Property<String> getImportLogFileName();

	@Override
	protected void beforeRun(PliSchemaImportCommand command) {
		command.setConfigurationFile(getConfigurationFile().get().getAsFile());
		command.setEncoding(getEncoding().get());
		if (getOutputFileName().isPresent()) {
			command.setOutputFileName(getOutputFileName().get());
		}
		if (getImportLogDirectory().isPresent()) {
			command.setImportLogDirectory(getImportLogDirectory().get().getAsFile());
		}
		if (getImportLogFileName().isPresent()) {
			command.setImportLogFileName(getImportLogFileName().get());
		}
	}

	@Override
	protected PliSchemaImportCommand createCommand() {
		return new PliSchemaImportCommand();
	}
}
