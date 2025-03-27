/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-gradle-plugin.
 *
 * sqlapp-gradle-plugin is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-gradle-plugin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-gradle-plugin.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.gradle.plugins.tasks;

import org.gradle.api.tasks.TaskAction;

import com.sqlapp.data.db.command.version.VersionUpCommand;
import com.sqlapp.gradle.plugins.extension.VersionUpExtension;

public abstract class VersionUpTask extends AbstractTask {

	@TaskAction
	public void exec() {
		final VersionUpCommand command = createCommand();
		final VersionUpExtension obj = getExtension();
		initialize(command, obj);
		run(command);
	}

	protected VersionUpExtension getExtension() {
		final VersionUpExtension obj = (VersionUpExtension) this.getProject().getExtensions().getByName("versionUp");
		return obj;
	}

	protected VersionUpCommand createCommand() {
		final VersionUpCommand command = new VersionUpCommand();
		return command;
	}

	protected void initialize(final VersionUpCommand command, final VersionUpExtension obj) {
		obj.setCommand(command, getDebug().getOrElse(false));
	}
}
