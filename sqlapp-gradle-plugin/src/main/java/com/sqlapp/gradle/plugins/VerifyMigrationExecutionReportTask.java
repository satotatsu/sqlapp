/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import org.gradle.api.Action;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import com.sqlapp.data.db.command.migration.VerifyMigrationExecutionReportCommand;

/** Verifies a migration execution report without opening a database connection. */
public abstract class VerifyMigrationExecutionReportTask extends AbstractTask<VerifyMigrationExecutionReportCommand> {
	public VerifyMigrationExecutionReportTask() {
		getRequireSuccessful().convention(false);
	}

	public void call(final Action<VerifyMigrationExecutionReportTask> action) {
		action.execute(this);
	}

	@InputFile
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getReportFile();

	@Input
	@Optional
	public abstract Property<String> getExpectedReportFingerprint();

	@Input
	public abstract Property<Boolean> getRequireSuccessful();

	@Override
	protected void beforeRun(final VerifyMigrationExecutionReportCommand command) {
		command.setReportFile(getReportFile().get().getAsFile());
		if (getExpectedReportFingerprint().isPresent()) {
			command.setExpectedReportFingerprint(getExpectedReportFingerprint().get());
		}
		command.setRequireSuccessful(getRequireSuccessful().get());
	}

	@Override
	protected VerifyMigrationExecutionReportCommand createCommand() {
		return new VerifyMigrationExecutionReportCommand();
	}
}
