package com.sqlapp.gradle.plugins.tasks;

import org.gradle.api.tasks.TaskAction;

import com.sqlapp.data.db.command.CountAllTablesCommand;
import com.sqlapp.gradle.plugins.extension.CountAllTableExtension;

public abstract class CountAllTableTask extends AbstractTask {

	@TaskAction
	public void exec() {
		final CountAllTablesCommand command = new CountAllTablesCommand();
		final CountAllTableExtension obj = this.getProject().getExtensions().getByType(CountAllTableExtension.class);
		obj.setCommand(command, this.getDebug().getOrElse(false));
		run(command);
	}
}
