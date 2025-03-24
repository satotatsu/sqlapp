package com.sqlapp.gradle.plugins.tasks;

import org.gradle.api.tasks.TaskAction;

import com.sqlapp.data.db.command.html.GenerateHtmlCommand;
import com.sqlapp.gradle.plugins.extension.GenerateHtmlExtension;

public abstract class GenerateHtmlTask extends AbstractTask {

	@TaskAction
	public void exec() {
		final GenerateHtmlCommand command = new GenerateHtmlCommand();
		final GenerateHtmlExtension obj = this.getProject().getExtensions().getByType(GenerateHtmlExtension.class);
		obj.setCommand(command, getDebug().getOrElse(false));
		run(command);
	}
}
