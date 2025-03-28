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

package com.sqlapp.gradle.plugins.extension;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.export.ExportData2FileCommand;
import com.sqlapp.data.schemas.rowiterator.WorkbookFileType;

/**
 * ExportData用のExtension
 */
public abstract class ExportDataExtension extends AbstractExportDataExtension {
	@Inject
	public ExportDataExtension(Project project) {
		super(project);
	}

	@Internal
	public void call(Action<ExportDataExtension> cons) {
		cons.execute(this);
	}

	/**
	 * Export対象が指定されなかった場合のExportをデフォルトとする
	 */
	@Input
	@Optional
	public abstract Property<Boolean> getDefaultExport();

	/**
	 * Output File Type
	 */
	@Input
	@Optional
	public abstract Property<String> getOutputFileType();

	@Input
	@Optional
	public abstract Property<String> getSheetName();

	@Input
	@Optional
	public abstract Property<Converters> getConverters();

	@Internal
	@Override
	public void setCommand(AbstractCommand command, boolean debug) {
		super.setCommand(command, debug);
		if (command instanceof ExportData2FileCommand) {
			ExportData2FileCommand com = (ExportData2FileCommand) command;
			if (getDefaultExport().isPresent()) {
				com.setDefaultExport(getDefaultExport().get());
			}
			if (getOutputFileType().isPresent()) {
				com.setOutputFileType(WorkbookFileType.parse(getOutputFileType().get()));
			}
			if (getSheetName().isPresent()) {
				com.setSheetName(getSheetName().get());
			}
			if (getConverters().isPresent()) {
				com.setConverters(getConverters().get());
			}
		}
	}
}
