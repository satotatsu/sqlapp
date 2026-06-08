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

import java.util.function.Consumer;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.ExportXmlCommand;
import com.sqlapp.data.schemas.DbObject;
import com.sqlapp.gradle.plugins.properties.ObjectTargetTaskProperty;
import com.sqlapp.gradle.plugins.properties.OutputDirectoryTaskProperty;
import com.sqlapp.gradle.plugins.properties.SchemaOptionTaskProperty;

@DisableCachingByDefault
public abstract class ExportXmlTask extends AbstractExportDataTask<ExportXmlCommand, Void>
		implements ObjectTargetTaskProperty, SchemaOptionTaskProperty, OutputDirectoryTaskProperty {
	@Inject
	public ExportXmlTask(ObjectFactory objectFactory) {
		super(objectFactory);
	}

	public void call(Action<ExportXmlTask> cons) {
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

	@Override
	protected void beforeRun(ExportXmlCommand command) {
		if (getTarget().isPresent()) {
			command.setTarget(getTarget().get());
		}
		if (getOutputFileName().isPresent()) {
			command.setOutputFileName(getOutputFileName().get());
		}
		if (getDumpRows().isPresent()) {
			command.setDumpRows(getDumpRows().get());
		}
		if (getIncludeRowDumpTables().isPresent() && !getIncludeRowDumpTables().get().isEmpty()) {
			command.setIncludeRowDumpTables(getIncludeRowDumpTables().get().toArray(new String[0]));
		}
		if (getExcludeRowDumpTables().isPresent() && !getExcludeRowDumpTables().get().isEmpty()) {
			command.setExcludeRowDumpTables(getExcludeRowDumpTables().get().toArray(new String[0]));
		}
		if (getConverter().isPresent()) {
			command.setConverter(getConverter().get());
		}
	}

	@Override
	protected ExportXmlCommand createCommand() {
		return new ExportXmlCommand();
	}

	@Override
	protected Void createExtension(Project project) {
		return null;
	}
}
