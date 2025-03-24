package com.sqlapp.gradle.plugins.tasks;

import org.gradle.api.tasks.TaskAction;

import com.sqlapp.data.db.command.export.ExportData2FileCommand;
import com.sqlapp.gradle.plugins.extension.ExportDataExtension;

public abstract class ExportDataTask extends AbstractTask {

	@TaskAction
	public void exec() {
		final ExportData2FileCommand command = new ExportData2FileCommand();
		final ExportDataExtension obj = this.getProject().getExtensions().getByType(ExportDataExtension.class);
		obj.setCommand(command, getDebug().getOrElse(false));
		run(command);
	}
}
