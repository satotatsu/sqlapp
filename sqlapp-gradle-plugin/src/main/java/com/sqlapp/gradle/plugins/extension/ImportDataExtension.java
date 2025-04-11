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

import java.io.File;
import java.util.function.Predicate;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.export.ImportDataFromFileCommand;
import com.sqlapp.data.db.sql.SqlType;

/**
 * ImportData用のExtension
 */
public abstract class ImportDataExtension extends AbstractExportDataExtension {
	@Inject
	public ImportDataExtension(Project project) {
		super(project);
	}

	@Internal
	public void call(Action<ImportDataExtension> cons) {
		cons.execute(this);
	}

	@Input
	@Optional
	public abstract Property<Boolean> getUseTableNameDirectory();

	@Input
	@Optional
	public abstract Property<Long> getQueryCommitInterval();

	/** file directory */
	@InputDirectory
	@Optional
	public abstract DirectoryProperty getFileDirectory();

	/** SQL Type */
	@Input
	@Optional
	public abstract Property<String> getSqlType();

	@Input
	@Optional
	private Predicate<File> fileFilter;

	public Predicate<File> getFileFilter() {
		return this.fileFilter;
	}

	public void setFileFilter(Predicate<File> fileFilter) {
		this.fileFilter = fileFilter;
	}

	public void fileFilter(Predicate<File> fileFilter) {
		this.fileFilter = fileFilter;
	}

	@Input
	@Optional
	public abstract Property<String> getPlaceholderPrefix();

	@Input
	@Optional
	public abstract Property<String> getPlaceholderSuffix();

	@Input
	@Optional
	public abstract Property<Boolean> getPlaceholders();

	@Internal
	@Override
	public void setCommand(AbstractCommand command) {
		super.setCommand(command);
		if (command instanceof ImportDataFromFileCommand) {
			ImportDataFromFileCommand com = (ImportDataFromFileCommand) command;
			if (getUseTableNameDirectory().isPresent()) {
				com.setUseTableNameDirectory(getUseTableNameDirectory().get());
			}
			if (getQueryCommitInterval().isPresent()) {
				com.setQueryCommitInterval(getQueryCommitInterval().get());
			}
			if (getFileDirectory().isPresent()) {
				com.setFileDirectory(getFileDirectory().get().getAsFile());
			}
			if (getSqlType().isPresent()) {
				com.setSqlType(SqlType.parse(getSqlType().get()));
			}
			if (getFileFilter() != null) {
				com.setFileFilter(getFileFilter());
			}
			//
			if (getPlaceholderPrefix().isPresent()) {
				com.setPlaceholderPrefix(getPlaceholderPrefix().get());
			}
			if (getPlaceholderSuffix().isPresent()) {
				com.setPlaceholderSuffix(getPlaceholderSuffix().get());
			}
			if (getPlaceholders().isPresent()) {
				com.setPlaceholders(getPlaceholders().get());
			}
		}
	}
}
