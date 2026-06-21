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

import java.io.File;
import java.util.function.Predicate;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.export.ImportDataCommand;
import com.sqlapp.gradle.plugins.properties.CommitPerTableTaskProperty;
import com.sqlapp.gradle.plugins.properties.CsvEncodingTaskProperty;
import com.sqlapp.gradle.plugins.properties.DirectoryTaskProperty;
import com.sqlapp.gradle.plugins.properties.FileDirectoryTaskProperty;
import com.sqlapp.gradle.plugins.properties.FileFilterTaskProperty;
import com.sqlapp.gradle.plugins.properties.FilesTaskProperty;
import com.sqlapp.gradle.plugins.properties.PlaceholderTaskProperty;
import com.sqlapp.gradle.plugins.properties.QueryCommitIntervalTaskProperty;
import com.sqlapp.gradle.plugins.properties.SqlTypeTaskProperty;

@DisableCachingByDefault
public abstract class ImportDataTask extends AbstractExportDataTask<ImportDataCommand, Void>
		implements FileDirectoryTaskProperty, FileFilterTaskProperty, FilesTaskProperty,
		QueryCommitIntervalTaskProperty, SqlTypeTaskProperty, DirectoryTaskProperty, PlaceholderTaskProperty,
		CommitPerTableTaskProperty, CsvEncodingTaskProperty {
	@Inject
	public ImportDataTask(ObjectFactory objectFactory) {
		super(objectFactory);
	}

	public void call(Action<ImportDataTask> cons) {
		cons.execute(this);
	}

	private Predicate<File> fileFilter = f -> true;

	@Input
	@Optional
	@Override
	public Predicate<File> getFileFilter() {
		return this.fileFilter;
	}

	@Override
	public void setFileFilter(Predicate<File> fileFilter) {
		this.fileFilter = fileFilter;
	}

	@Override
	protected ImportDataCommand createCommand() {
		return new ImportDataCommand();
	}

	@Override
	protected Void createExtension(Project project) {
		return null;
	}
}
