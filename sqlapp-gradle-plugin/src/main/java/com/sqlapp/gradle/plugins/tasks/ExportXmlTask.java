package com.sqlapp.gradle.plugins.tasks;

import org.gradle.api.tasks.TaskAction;

import com.sqlapp.data.db.command.ExportXmlCommand;
import com.sqlapp.gradle.plugins.extension.ExportXmlExtension;

public abstract class ExportXmlTask extends AbstractTask {

	@TaskAction
	public void exec() {
		final ExportXmlCommand command = new ExportXmlCommand();
		final ExportXmlExtension obj = this.getProject().getExtensions().getByType(ExportXmlExtension.class);
		obj.setCommand(command, getDebug().getOrElse(false));
		run(command);
	}
}
