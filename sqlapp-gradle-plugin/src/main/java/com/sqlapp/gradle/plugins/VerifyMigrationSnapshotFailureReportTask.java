/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import org.gradle.api.Action;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.Optional;

import com.sqlapp.data.db.command.migration.VerifyMigrationSnapshotFailureReportCommand;

/** Verifies an SCD2 failure report against its exact approval artifact. */
public abstract class VerifyMigrationSnapshotFailureReportTask
		extends AbstractTask<VerifyMigrationSnapshotFailureReportCommand> {
	public void call(final Action<VerifyMigrationSnapshotFailureReportTask> action) {
		action.execute(this);
	}

	@InputFile
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getReportFile();

	@InputFile
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getApprovalFile();

	@Optional
	@InputFile
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getConfigurationFile();

	@Override
	protected void beforeRun(final VerifyMigrationSnapshotFailureReportCommand command) {
		command.setReportFile(getReportFile().get().getAsFile());
		command.setApprovalFile(getApprovalFile().get().getAsFile());
		if (getConfigurationFile().isPresent()) {
			command.setConfigurationFile(getConfigurationFile().get().getAsFile());
		}
	}

	@Override
	protected VerifyMigrationSnapshotFailureReportCommand createCommand() {
		return new VerifyMigrationSnapshotFailureReportCommand();
	}
}
