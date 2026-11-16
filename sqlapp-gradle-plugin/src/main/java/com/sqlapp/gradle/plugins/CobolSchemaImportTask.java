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

import com.sqlapp.data.db.command.host.cobol.CobolSchemaImportCommand;
import com.sqlapp.gradle.plugins.properties.OutputDirectoryTaskProperty;
import com.sqlapp.gradle.plugins.properties.TargetFileTaskProperty;

/**
 * Gradle task for importing COBOL copybook records as schema XML.
 */
@DisableCachingByDefault
public abstract class CobolSchemaImportTask extends AbstractTask<CobolSchemaImportCommand>
		implements TargetFileTaskProperty, OutputDirectoryTaskProperty {

	public CobolSchemaImportTask() {
		getEncoding().convention("UTF-8");
		getMigrationMappingEnabled().convention(true);
	}

	public void call(Action<CobolSchemaImportTask> action) {
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
	public abstract DirectoryProperty getMigrationMappingDirectory();

	@Input
	@Optional
	public abstract Property<String> getMigrationMappingFileName();

	@Input
	public abstract Property<Boolean> getMigrationMappingEnabled();

	@Override
	protected void beforeRun(CobolSchemaImportCommand command) {
		command.setConfigurationFile(getConfigurationFile().get().getAsFile());
		command.setEncoding(getEncoding().get());
		command.setMigrationMappingEnabled(getMigrationMappingEnabled().get());
		if (getOutputFileName().isPresent()) {
			command.setOutputFileName(getOutputFileName().get());
		}
		if (getMigrationMappingDirectory().isPresent()) {
			command.setMigrationMappingDirectory(getMigrationMappingDirectory().get().getAsFile());
		}
		if (getMigrationMappingFileName().isPresent()) {
			command.setMigrationMappingFileName(getMigrationMappingFileName().get());
		}
	}

	@Override
	protected CobolSchemaImportCommand createCommand() {
		return new CobolSchemaImportCommand();
	}
}
