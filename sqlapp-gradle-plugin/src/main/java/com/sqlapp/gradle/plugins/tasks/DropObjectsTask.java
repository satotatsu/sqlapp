package com.sqlapp.gradle.plugins.tasks;

import org.gradle.api.tasks.TaskAction;

import com.sqlapp.data.db.command.DropObjectsCommand;
import com.sqlapp.gradle.plugins.extension.DropObjectsExtension;

public abstract class DropObjectsTask extends AbstractTask {

	@TaskAction
	public void exec() {
		final DropObjectsCommand command = new DropObjectsCommand();
		final DropObjectsExtension obj = this.getProject().getExtensions().getByType(DropObjectsExtension.class);
		obj.setCommand(command, getDebug().getOrElse(false));
		run(command);
	}
}
