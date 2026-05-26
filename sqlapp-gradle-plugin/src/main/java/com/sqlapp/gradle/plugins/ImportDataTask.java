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
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.export.ImportDataFromFileCommand;
import com.sqlapp.gradle.plugins.extension.ImportDataExtension;

@DisableCachingByDefault
public abstract class ImportDataTask extends AbstractTask<ImportDataFromFileCommand, ImportDataExtension> {

	public ImportDataTask() {
	}

	@Override
	protected ImportDataFromFileCommand createCommand() {
		return new ImportDataFromFileCommand();
	}

	@Override
	protected ImportDataExtension createExtension(Project project) {
		final ImportDataExtension obj = project.getExtensions().getByType(ImportDataExtension.class);
		return obj;
	}

	@Override
	protected void exec(ImportDataFromFileCommand command, ImportDataExtension obj) {
		run(command);
	}
}
