/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.gradle.plugins;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import com.sqlapp.data.db.command.SqlExecuteCommand;
import com.sqlapp.gradle.plugins.properties.DataSourceTaskProperty;
import com.sqlapp.gradle.plugins.properties.EncodingTaskProperty;
import com.sqlapp.gradle.plugins.properties.PlaceholderTaskProperty;

public abstract class SqlExecuteTask extends AbstractDbTask<SqlExecuteCommand, Void>
		implements DataSourceTaskProperty, EncodingTaskProperty, PlaceholderTaskProperty {

	public SqlExecuteTask() {
	}

	@Internal
	public void call(Action<SqlExecuteTask> cons) {
		cons.execute(this);
	}

	@Input
	@Optional
	public abstract Property<String> getSqlText();

	@InputFile
	@Optional
	public abstract ConfigurableFileCollection getSqlFiles();

	@Override
	protected SqlExecuteCommand createCommand() {
		return new SqlExecuteCommand();
	}

	@Override
	protected void exec(SqlExecuteCommand command, Void obj) {
		if (getSqlText().isPresent()) {
			command.setSqlText(getSqlText().get());
		}
		if (!getSqlFiles().isEmpty()) {
			command.setSqlFiles(getSqlFiles().getFiles());
		}
		run(command);
	}

	@Override
	protected Void createExtension(Project project) {
		return null;
	}

}