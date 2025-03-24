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

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

import com.sqlapp.data.db.command.OutputFormatType;
import com.sqlapp.data.db.command.SqlQueryCommand;
import com.sqlapp.gradle.plugins.extension.DataSourceInject;
import com.sqlapp.util.FileUtils;

public abstract class SqlQueryTask extends AbstractDbTask implements DataSourceInject {
	@Input
	@Optional
	public abstract Property<String> getSql();

	/**
	 * Output targetFile
	 */
	@InputFile
	@Optional
	public abstract RegularFileProperty getSqlFile();

	/** encoding */
	@Input
	@Optional
	public abstract Property<String> getEncoding();

	/** encoding */
	@Input
	@Optional
	public abstract Property<String> getOutputFormatType();

	@Input
	@Optional
	OutputFormatType outputFormatType = null;

	@TaskAction
	public void exec() {
		SqlQueryCommand command = new SqlQueryCommand();
		command.setDataSource(createDataSource(this.getDataSource()));
		if (getSql().isPresent()) {
			command.setSql(getSql().get());
		}
		if (!getSqlFile().isPresent()) {
			command.setSql(FileUtils.readText(getSqlFile().get().getAsFile(),
					getEncoding().isPresent() ? getEncoding().get() : "UTF-8"));
		}
		if (getOutputFormatType().isPresent()) {
			command.setOutputFormatType(OutputFormatType.parse(getOutputFormatType().get()));
		}
		run(command);
	}

}