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

import org.gradle.api.Action;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

import com.sqlapp.data.db.command.generator.GenerateGeneratorSettingCommand;
import com.sqlapp.data.db.command.generator.GeneratorSettingFileType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.gradle.plugins.extension.DataSourceExtension;
import com.sqlapp.gradle.plugins.extension.DataSourceInject;
import com.sqlapp.gradle.plugins.extension.TableOptionsExtension;

public abstract class GenerateDataGeneratorSettingTask extends AbstractDbTask implements DataSourceInject {

	public GenerateDataGeneratorSettingTask() {
		setDataSource(getProject().getObjects().newInstance((DataSourceExtension.class)));
		getTableOptions().convention(getProject().getObjects().newInstance(TableOptionsExtension.class));
	}

	@Internal
	public void call(Action<GenerateDataGeneratorSettingTask> cons) {
		cons.execute(this);
	}

	@Input
	@Optional
	public abstract Property<String> getSchemaName();

	@Input
	@Optional
	public abstract Property<String> getTableName();

	@Input
	@Optional
	public abstract DirectoryProperty getDirectory();

	@Input
	@Optional
	public abstract Property<String> getSqlType();

	@Input
	@Optional
	public abstract Property<String> getFileType();

	@Nested
	public abstract Property<TableOptionsExtension> getTableOptions();

	public void tableOptions(Action<? super TableOptionsExtension> action) {
		action.execute(getTableOptions().get());
	}

	@TaskAction
	public void exec() {
		final GenerateGeneratorSettingCommand command = new GenerateGeneratorSettingCommand();
		command.setDataSource(createDataSource(this.getDataSource()));
		if (getSchemaName().isPresent()) {
			command.setSchemaName(getSchemaName().get());
		}
		if (getTableName().isPresent()) {
			command.setTableName(getTableName().get());
		}
		if (getDirectory().isPresent()) {
			command.setDirectory(getDirectory().get().getAsFile());
		}
		if (getSqlType().isPresent()) {
			command.setSqlType(SqlType.parse(getSqlType().get()));
		}
		if (getTableOptions().isPresent()) {
			command.setTableOptions(getTableOptions().get());
		}
		if (getFileType().isPresent()) {
			command.setFileType(GeneratorSettingFileType.parse(getFileType().get()));
		}
		run(command);
	}

}