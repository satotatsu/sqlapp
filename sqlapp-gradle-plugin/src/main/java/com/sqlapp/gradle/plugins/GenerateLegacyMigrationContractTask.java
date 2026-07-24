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

import com.sqlapp.data.db.command.migration.GenerateLegacyMigrationContractCommand;

/**
 * Gradle task for generating the legacy extraction and load contract.
 */
@DisableCachingByDefault
public abstract class GenerateLegacyMigrationContractTask
		extends AbstractTask<GenerateLegacyMigrationContractCommand> {

	public GenerateLegacyMigrationContractTask() {
		getEncoding().convention("UTF-8");
		getDelimiter().convention(",");
		getQuote().convention("\"");
		getNullValue().convention("");
		getHeader().convention(true);
		getRecordSeparator().convention("CRLF");
	}

	public void call(Action<GenerateLegacyMigrationContractTask> action) {
		action.execute(this);
	}

	@InputFile
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getMappingFile();

	@OutputDirectory
	public abstract DirectoryProperty getOutputDirectory();

	@Input
	@Optional
	public abstract Property<String> getOutputFileName();

	@Input
	public abstract Property<String> getEncoding();

	@Input
	public abstract Property<String> getDelimiter();

	@Input
	public abstract Property<String> getQuote();

	@Input
	public abstract Property<String> getNullValue();

	@Input
	public abstract Property<Boolean> getHeader();

	@Input
	public abstract Property<String> getRecordSeparator();

	@Override
	protected void beforeRun(GenerateLegacyMigrationContractCommand command) {
		command.setMappingFile(getMappingFile().get().getAsFile());
		command.setOutputDirectory(getOutputDirectory().get().getAsFile());
		command.setEncoding(getEncoding().get());
		command.setDelimiter(getDelimiter().get());
		command.setQuote(getQuote().get());
		command.setNullValue(getNullValue().get());
		command.setHeader(getHeader().get());
		command.setRecordSeparator(getRecordSeparator().get());
		if (getOutputFileName().isPresent()) {
			command.setOutputFileName(getOutputFileName().get());
		}
	}

	@Override
	protected GenerateLegacyMigrationContractCommand createCommand() {
		return new GenerateLegacyMigrationContractCommand();
	}
}
