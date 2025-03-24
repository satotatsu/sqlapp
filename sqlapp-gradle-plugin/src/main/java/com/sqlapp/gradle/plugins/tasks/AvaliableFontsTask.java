package com.sqlapp.gradle.plugins.tasks;

import org.gradle.api.tasks.TaskAction;

import com.sqlapp.data.db.command.AvailableFontsCommand;

public abstract class AvaliableFontsTask extends AbstractTask {

	@TaskAction
	public void exec() {
		AvailableFontsCommand command = new AvailableFontsCommand();
		run(command);
	}
}