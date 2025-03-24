package com.sqlapp.gradle.plugins.tasks;

import com.sqlapp.data.db.command.version.VersionDownCommand;
import com.sqlapp.data.db.command.version.VersionUpCommand;
import com.sqlapp.gradle.plugins.extension.VersionUpExtension;

public abstract class VersionDownTask extends VersionUpTask {

	@Override
	protected VersionUpCommand createCommand() {
		final VersionDownCommand command = new VersionDownCommand();
		return command;
	}

	@Override
	protected void initialize(final VersionUpCommand command, final VersionUpExtension obj) {
		super.initialize(command, obj);
		final VersionUpExtension ext = (VersionUpExtension) this.getProject().getExtensions().getByName("versionDown");
		ext.setCommand(command, getDebug().getOrElse(false));
		command.setLastChangeToApply(null);
	}
}
