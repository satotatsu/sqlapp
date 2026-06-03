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

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.export.ExportData2FileCommand;
import com.sqlapp.gradle.plugins.properties.ConvertersTaskProperty;
import com.sqlapp.gradle.plugins.properties.CsvEncodingTaskProperty;
import com.sqlapp.gradle.plugins.properties.OutputDirectoryTaskProperty;
import com.sqlapp.gradle.plugins.properties.OutputFileTypeTaskProperty;
import com.sqlapp.gradle.plugins.properties.SheetNameTaskProperty;

@DisableCachingByDefault
public abstract class ExportDataTask extends AbstractExportDataTask<ExportData2FileCommand, Void>
		implements OutputFileTypeTaskProperty, OutputDirectoryTaskProperty, SheetNameTaskProperty,
		ConvertersTaskProperty, CsvEncodingTaskProperty {
	@Inject
	public ExportDataTask(ObjectFactory objectFactory) {
		super(objectFactory);
	}

	public void call(Action<ExportDataTask> cons) {
		cons.execute(this);
	}

	/**
	 * Export対象が指定されなかった場合のExportをデフォルトとする
	 */
	@Input
	@Optional
	public abstract Property<Boolean> getDefaultExport();

	@Override
	protected void beforeRun(ExportData2FileCommand command) {
		if (getDefaultExport().isPresent()) {
			command.setDefaultExport(getDefaultExport().get());
		}
	}

	@Override
	protected ExportData2FileCommand createCommand() {
		return new ExportData2FileCommand();
	}

	@Override
	protected Void createExtension(Project project) {
		return null;
	}

}
