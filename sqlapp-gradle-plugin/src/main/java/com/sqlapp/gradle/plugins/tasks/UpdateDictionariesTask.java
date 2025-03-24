package com.sqlapp.gradle.plugins.tasks;

import org.gradle.api.tasks.TaskAction;

import com.sqlapp.data.db.command.html.UpdateDictionariesCommand;
import com.sqlapp.gradle.plugins.extension.UpdateDictionariesExtension;

public abstract class UpdateDictionariesTask extends AbstractTask {

	@TaskAction
	public void exec() {
		final UpdateDictionariesCommand command = new UpdateDictionariesCommand();
		final UpdateDictionariesExtension obj = this.getProject().getExtensions()
				.getByType(UpdateDictionariesExtension.class);
		obj.setCommand(command, getDebug().getOrElse(false));
		run(command);
	}
}
