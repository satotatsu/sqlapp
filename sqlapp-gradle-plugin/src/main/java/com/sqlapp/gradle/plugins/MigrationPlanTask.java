/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.migration.MigrationCommand;
import com.sqlapp.data.db.command.migration.MigrationPlanCommand;

@DisableCachingByDefault
public abstract class MigrationPlanTask extends MigrationTask {
	@OutputFile
	@Optional
	public abstract RegularFileProperty getOutputFile();

	@Override
	protected MigrationCommand createCommand() {
		return new MigrationPlanCommand();
	}

	@Override
	protected void initializeCommand(final MigrationCommand command) {
		super.initializeCommand(command);
		if (getOutputFile().isPresent()) {
			((MigrationPlanCommand) command).setOutputFile(getOutputFile().get().getAsFile());
		}
	}
}
