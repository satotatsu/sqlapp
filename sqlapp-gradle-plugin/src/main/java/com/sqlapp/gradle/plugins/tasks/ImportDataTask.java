package com.sqlapp.gradle.plugins.tasks;

import org.gradle.api.tasks.TaskAction;

import com.sqlapp.data.db.command.export.ImportDataFromFileCommand;
import com.sqlapp.gradle.plugins.extension.ImportDataExtension;

public abstract class ImportDataTask extends AbstractTask {

	@TaskAction
	public void exec() {
		final ImportDataFromFileCommand command = new ImportDataFromFileCommand();
		final ImportDataExtension obj = this.getProject().getExtensions().getByType(ImportDataExtension.class);
		obj.setCommand(command, getDebug().getOrElse(false));
		run(command);
	}
}
