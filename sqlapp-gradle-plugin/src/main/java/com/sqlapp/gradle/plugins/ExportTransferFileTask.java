/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.work.DisableCachingByDefault;
import com.sqlapp.data.db.command.migration.transfer.ExportTransferFileCommand;

/** Creates a fresh CSV package; directory and table selection are defined in the shared job. */
@DisableCachingByDefault(because = "Reads a live database and creates a unique export package")
public abstract class ExportTransferFileTask extends AbstractDbTask<ExportTransferFileCommand> {
	public ExportTransferFileTask() { getOutputs().upToDateWhen(task -> false); }
	@InputFile @PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getConfigurationFile();
	@Override protected void initializeCommand(ExportTransferFileCommand command) {
		beforeRun(command);
		try { command.validateInputs(); }
		catch (Exception failure) { throw new org.gradle.api.GradleException("Invalid CSV export job.", failure); }
		super.initializeCommand(command);
	}
	@Override protected void beforeRun(ExportTransferFileCommand command) {
		command.setConfigurationFile(getConfigurationFile().get().getAsFile());
	}
	@Override protected ExportTransferFileCommand createCommand() { return new ExportTransferFileCommand(); }
}
