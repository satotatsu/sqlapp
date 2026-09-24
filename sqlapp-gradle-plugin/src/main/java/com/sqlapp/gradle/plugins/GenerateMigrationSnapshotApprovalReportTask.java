/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import org.gradle.api.Action;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import com.sqlapp.data.db.command.migration.snapshot.GenerateMigrationSnapshotApprovalReportCommand;

/** Generates a connection-free approval artifact for one SCD2 snapshot. */
public abstract class GenerateMigrationSnapshotApprovalReportTask
		extends AbstractTask<GenerateMigrationSnapshotApprovalReportCommand> {
	public GenerateMigrationSnapshotApprovalReportTask() {
		// schemaFile is resolved from YAML at execution time and is therefore an
		// indirect input.
		getOutputs().upToDateWhen(task -> false);
	}

	public void call(final Action<GenerateMigrationSnapshotApprovalReportTask> action) {
		action.execute(this);
	}

	@InputFile
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getConfigurationFile();

	@OutputFile
	public abstract RegularFileProperty getTargetFile();

	@Override
	protected void beforeRun(final GenerateMigrationSnapshotApprovalReportCommand command) {
		command.setConfigurationFile(getConfigurationFile().get().getAsFile());
		command.setTargetFile(getTargetFile().get().getAsFile());
	}

	@Override
	protected GenerateMigrationSnapshotApprovalReportCommand createCommand() {
		return new GenerateMigrationSnapshotApprovalReportCommand();
	}
}
