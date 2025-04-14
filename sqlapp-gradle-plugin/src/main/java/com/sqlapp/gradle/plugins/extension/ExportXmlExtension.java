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

import java.util.function.Consumer;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.ExportXmlCommand;
import com.sqlapp.data.schemas.DbObject;
import com.sqlapp.gradle.plugins.properties.ObjectTargetTaskProperty;
import com.sqlapp.gradle.plugins.properties.OutputDirectoryTaskProperty;
import com.sqlapp.gradle.plugins.properties.SchemaOptionTaskProperty;

/**
 * ExportData用のExtension
 */
public abstract class ExportXmlExtension extends AbstractExportDataExtension
		implements ObjectTargetTaskProperty, SchemaOptionTaskProperty, OutputDirectoryTaskProperty {
	@Inject
	public ExportXmlExtension(Project project) {
		super(project);
	}

	@Internal
	public void call(Action<ExportXmlExtension> cons) {
		cons.execute(this);
	}

	/**
	 * 対象オブジェクト
	 */
	@Input
	@Optional
	public abstract Property<String> getTarget();

	/**
	 * Output FileName
	 */
	@Input
	@Optional
	public abstract Property<String> getOutputFileName();

	/**
	 * 行のダンプ
	 */
	@Input
	@Optional
	public abstract Property<Boolean> getDumpRows();

	/**
	 * 行のダンプを行うテーブル
	 */
	@Input
	@Optional
	public abstract ListProperty<String> getIncludeRowDumpTables();

	/**
	 * 行のダンプから除くテーブル
	 */
	@Input
	@Optional
	public abstract ListProperty<String> getExcludeRowDumpTables();

	@Input
	@Optional
	public abstract Property<Consumer<DbObject<?>>> getConverter();

	@Internal
	@Override
	public void setCommand(AbstractCommand command) {
		super.setCommand(command);
		if (command instanceof ExportXmlCommand) {
			ExportXmlCommand com = (ExportXmlCommand) command;
			if (getTarget().isPresent()) {
				com.setTarget(getTarget().get());
			}
			if (getOutputFileName().isPresent()) {
				com.setOutputFileName(getOutputFileName().get());
			}
			if (getDumpRows().isPresent()) {
				com.setDumpRows(getDumpRows().get());
			}
			if (getIncludeRowDumpTables().isPresent()) {
				com.setIncludeRowDumpTables(getIncludeRowDumpTables().get().toArray(new String[0]));
			}
			if (getExcludeRowDumpTables().isPresent()) {
				com.setExcludeRowDumpTables(getExcludeRowDumpTables().get().toArray(new String[0]));
			}
			if (getConverter().isPresent()) {
				com.setConverter(getConverter().get());
			}
		}
	}
}
