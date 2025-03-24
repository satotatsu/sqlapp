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

package com.sqlapp.gradle.plugins.tasks;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

import com.sqlapp.data.db.command.SqlExecuteCommand;
import com.sqlapp.gradle.plugins.extension.DataSourceInject;
import com.sqlapp.gradle.plugins.extension.PlaceholderInject;

public abstract class SqlExecuteTask extends AbstractDbTask implements DataSourceInject, PlaceholderInject {
	@Input
	@Optional
	public abstract Property<String> getSqlText();

	@InputFile
	@Optional
	public abstract ConfigurableFileCollection getSqlFiles();

	/** encoding */
	@Input
	@Optional
	public abstract Property<String> getEncoding();

	@Input
	@Optional
	public abstract Property<String> getPlaceholderPrefix();

	@Input
	@Optional
	public abstract Property<String> getPlaceholderSuffix();

	@Input
	@Optional
	public abstract Property<Boolean> getPlaceholders();

	@TaskAction
	public void exec() {
		final SqlExecuteCommand command = new SqlExecuteCommand();
		command.setDataSource(createDataSource(this.getDataSource()));
		if (getSqlText().isPresent()) {
			command.setSqlText(getSqlText().get());
		}
		if (!getSqlFiles().isEmpty()) {
			command.setSqlFiles(getSqlFiles().getFiles());
		}
		if (getEncoding().isPresent()) {
			command.setEncoding(getEncoding().get());
		}
		this.setPlaceholders(command);
		run(command);
	}

}