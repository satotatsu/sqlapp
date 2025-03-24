package com.sqlapp.gradle.plugins.tasks;

import com.sqlapp.data.db.command.version.VersionInsertCommand;
import com.sqlapp.data.db.command.version.VersionUpCommand;
import com.sqlapp.gradle.plugins.extension.VersionUpExtension;

public abstract class VersionInsertTask extends VersionUpTask {

	@Override
	protected VersionUpCommand createCommand() {
		final VersionInsertCommand command = new VersionInsertCommand();
		return command;
	}

	@Override
	protected void initialize(final VersionUpCommand command, final VersionUpExtension obj) {
		super.initialize(command, obj);
		final VersionUpExtension ext = (VersionUpExtension) this.getProject().getExtensions()
				.getByName("versionInsert");
		ext.setCommand(command, getDebug().getOrElse(false));
	}
}
