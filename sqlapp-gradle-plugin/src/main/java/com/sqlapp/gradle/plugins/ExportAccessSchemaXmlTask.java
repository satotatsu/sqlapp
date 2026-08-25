/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import com.sqlapp.data.db.command.ExportSchemaFileXmlCommand;

/** Exports an Access MDB/ACCDB file as sqlapp Schema XML. */
public abstract class ExportAccessSchemaXmlTask
		extends AbstractTask<ExportSchemaFileXmlCommand> {

	@InputFile
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getInputFile();

	@OutputFile
	public abstract RegularFileProperty getOutputFile();

	@Input
	@Optional
	public abstract Property<String> getSchemaName();

	@Input
	public abstract Property<Boolean> getDumpRows();

	@Input
	@Optional
	public abstract ListProperty<String> getIncludeRowDumpTables();

	@Input
	@Optional
	public abstract ListProperty<String> getExcludeRowDumpTables();

	public ExportAccessSchemaXmlTask() {
		getDumpRows().convention(true);
	}

	@Override
	protected void beforeRun(final ExportSchemaFileXmlCommand command) {
		command.setInputFile(getInputFile().get().getAsFile());
		command.setOutputFile(getOutputFile().get().getAsFile());
		command.setDumpRows(getDumpRows().get());
		if (getSchemaName().isPresent()) {
			command.setSchemaName(getSchemaName().get());
		}
		if (getIncludeRowDumpTables().isPresent()) {
			command.setIncludeRowDumpTables(getIncludeRowDumpTables().get()
					.toArray(String[]::new));
		}
		if (getExcludeRowDumpTables().isPresent()) {
			command.setExcludeRowDumpTables(getExcludeRowDumpTables().get()
					.toArray(String[]::new));
		}
	}

	@Override
	protected ExportSchemaFileXmlCommand createCommand() {
		return new ExportSchemaFileXmlCommand();
	}
}
