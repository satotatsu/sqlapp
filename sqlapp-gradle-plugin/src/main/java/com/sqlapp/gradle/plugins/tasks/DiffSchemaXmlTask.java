package com.sqlapp.gradle.plugins.tasks;

import org.gradle.api.tasks.TaskAction;

import com.sqlapp.data.db.command.DiffCommand;
import com.sqlapp.gradle.plugins.extension.DiffSchemaXmlExtension;

public abstract class DiffSchemaXmlTask extends AbstractTask {

	@TaskAction
	public void exec() {
		final DiffCommand command = new DiffCommand();
		final DiffSchemaXmlExtension obj = this.getProject().getExtensions().getByType(DiffSchemaXmlExtension.class);
		obj.setCommand(command, getDebug().getOrElse(false));
		run(command);
	}
}
