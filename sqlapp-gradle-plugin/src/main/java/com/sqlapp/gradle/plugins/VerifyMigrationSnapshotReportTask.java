/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import org.gradle.api.Action;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import com.sqlapp.data.db.command.migration.VerifyMigrationSnapshotReportCommand;

/** Verifies an SCD2 success report against its exact approval artifact. */
public abstract class VerifyMigrationSnapshotReportTask extends AbstractTask<VerifyMigrationSnapshotReportCommand> {
	public void call(final Action<VerifyMigrationSnapshotReportTask> action) {
		action.execute(this);
	}

	@InputFile
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getReportFile();

	@InputFile
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getApprovalFile();

	@Override
	protected void beforeRun(final VerifyMigrationSnapshotReportCommand command) {
		command.setReportFile(getReportFile().get().getAsFile());
		command.setApprovalFile(getApprovalFile().get().getAsFile());
	}

	@Override
	protected VerifyMigrationSnapshotReportCommand createCommand() {
		return new VerifyMigrationSnapshotReportCommand();
	}
}
