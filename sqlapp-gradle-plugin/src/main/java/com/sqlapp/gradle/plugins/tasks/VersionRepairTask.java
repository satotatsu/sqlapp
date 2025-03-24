package com.sqlapp.gradle.plugins.tasks;

import com.sqlapp.data.db.command.version.VersionRepairCommand;
import com.sqlapp.data.db.command.version.VersionUpCommand;
import com.sqlapp.gradle.plugins.extension.VersionUpExtension;

public abstract class VersionRepairTask extends VersionUpTask {

	@Override
	protected VersionUpCommand createCommand() {
		final VersionRepairCommand command = new VersionRepairCommand();
		return command;
	}

	@Override
	protected void initialize(final VersionUpCommand command, final VersionUpExtension obj) {
		super.initialize(command, obj);
		command.setLastChangeToApply(null);
	}
}
