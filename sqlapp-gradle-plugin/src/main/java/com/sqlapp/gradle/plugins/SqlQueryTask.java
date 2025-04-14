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
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import com.sqlapp.data.db.command.SqlQueryCommand;
import com.sqlapp.gradle.plugins.properties.DataSourceTaskProperty;
import com.sqlapp.gradle.plugins.properties.EncodingTaskProperty;
import com.sqlapp.gradle.plugins.properties.OutputFormatTypeTaskProperty;
import com.sqlapp.gradle.plugins.properties.SqlTaskProperty;
import com.sqlapp.util.FileUtils;

public abstract class SqlQueryTask extends AbstractDbTask<SqlQueryCommand, Void>
		implements DataSourceTaskProperty, OutputFormatTypeTaskProperty, EncodingTaskProperty, SqlTaskProperty {

	public SqlQueryTask() {
	}

	@Internal
	public void call(Action<SqlQueryTask> cons) {
		cons.execute(this);
	}

	/**
	 * Output targetFile
	 */
	@InputFile
	@Optional
	public abstract RegularFileProperty getSqlFile();

	@Override
	protected SqlQueryCommand createCommand() {
		return new SqlQueryCommand();
	}

	@Override
	protected void exec(SqlQueryCommand command, Void obj) {
		if (getSqlFile().isPresent()) {
			command.setSql(FileUtils.readText(getSqlFile().get().getAsFile(),
					getEncoding().isPresent() ? getEncoding().get() : "UTF-8"));
		}
		run(command);
	}

	@Internal
	@Override
	protected Void createExtension(Project project) {
		return null;
	}

}