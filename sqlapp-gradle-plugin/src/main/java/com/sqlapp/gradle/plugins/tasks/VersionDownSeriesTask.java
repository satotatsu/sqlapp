package com.sqlapp.gradle.plugins.tasks;

import com.sqlapp.data.db.command.version.SeriesVersionDownCommand;
import com.sqlapp.data.db.command.version.VersionUpCommand;

public abstract class VersionDownSeriesTask extends VersionUpTask {

	@Override
	protected VersionUpCommand createCommand() {
		final SeriesVersionDownCommand command = new SeriesVersionDownCommand();
		return command;
	}
}
