package com.sqlapp.gradle.plugins.tasks;

import org.gradle.api.tasks.TaskAction;

import com.sqlapp.data.db.command.version.VersionUpCommand;
import com.sqlapp.gradle.plugins.extension.VersionUpExtension;

public abstract class VersionUpTask extends AbstractTask {

	@TaskAction
	public void exec() {
		final VersionUpCommand command = createCommand();
		final VersionUpExtension obj = getExtension();
		initialize(command, obj);
		run(command);
	}

	protected VersionUpExtension getExtension() {
		final VersionUpExtension obj = (VersionUpExtension) this.getProject().getExtensions().getByName("versionUp");
		return obj;
	}

	protected VersionUpCommand createCommand() {
		final VersionUpCommand command = new VersionUpCommand();
		return command;
	}

	protected void initialize(final VersionUpCommand command, final VersionUpExtension obj) {
		obj.setCommand(command, getDebug().getOrElse(false));
	}
}
