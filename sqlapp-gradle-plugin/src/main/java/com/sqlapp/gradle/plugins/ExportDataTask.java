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

import org.gradle.api.Project;
import org.gradle.api.tasks.Internal;

import com.sqlapp.data.db.command.export.ExportData2FileCommand;
import com.sqlapp.gradle.plugins.extension.ExportDataExtension;

public abstract class ExportDataTask extends AbstractTask<ExportData2FileCommand, ExportDataExtension> {

	public ExportDataTask() {
	}

	@Override
	protected void exec(ExportData2FileCommand command, ExportDataExtension extension) {
		extension.setCommand(command);
		run(command);
	}

	@Override
	protected ExportData2FileCommand createCommand() {
		return new ExportData2FileCommand();
	}

	@Internal
	@Override
	protected ExportDataExtension createExtension(Project project) {
		final ExportDataExtension obj = project.getExtensions().getByType(ExportDataExtension.class);
		return obj;
	}

}
