/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-gradle-plugin.
 */
package com.sqlapp.gradle.plugins;

import org.gradle.api.Action;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.host.cobol.CobolCsvExtractCommand;
import com.sqlapp.gradle.plugins.properties.OutputDirectoryTaskProperty;

/**
 * Gradle task for reading a host data set with a COBOL storage layout and
 * writing the CSV files of a legacy migration contract.
 */
@DisableCachingByDefault
public abstract class ExtractCobolCsvTask extends AbstractTask<CobolCsvExtractCommand>
		implements OutputDirectoryTaskProperty {

	public void call(Action<ExtractCobolCsvTask> action) {
		action.execute(this);
	}

	@InputFile
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getContractFile();

	@InputFile
	@PathSensitive(PathSensitivity.RELATIVE)
	@Optional
	public abstract RegularFileProperty getMappingFile();

	@InputFile
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getConfigurationFile();

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getDataFile();

	@Override
	protected void beforeRun(CobolCsvExtractCommand command) {
		command.setContractFile(getContractFile().get().getAsFile());
		command.setConfigurationFile(getConfigurationFile().get().getAsFile());
		command.setDataFile(getDataFile().get().getAsFile());
		if (getMappingFile().isPresent()) {
			command.setMappingFile(getMappingFile().get().getAsFile());
		}
	}

	@Override
	protected CobolCsvExtractCommand createCommand() {
		return new CobolCsvExtractCommand();
	}
}
