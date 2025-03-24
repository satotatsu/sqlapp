package com.sqlapp.gradle.plugins.tasks;

import org.gradle.api.tasks.TaskAction;

import com.sqlapp.data.db.command.SynchronizeSchemaCommand;
import com.sqlapp.gradle.plugins.extension.SynchronizeSchemaExtension;

public abstract class SynchronizeSchemaTask extends AbstractTask {

	@TaskAction
	public void exec() {
		final SynchronizeSchemaCommand command = new SynchronizeSchemaCommand();
		final SynchronizeSchemaExtension obj = this.getProject().getExtensions()
				.getByType(SynchronizeSchemaExtension.class);
		obj.setCommand(command, getDebug().getOrElse(false));
		run(command);
	}
}
