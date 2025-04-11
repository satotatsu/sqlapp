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

import org.gradle.api.Action;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.command.export.ConvertDataFileCommand;
import com.sqlapp.data.schemas.rowiterator.WorkbookFileType;
import com.sqlapp.util.JsonConverter;

public abstract class ConvertDataFileTask extends AbstractTask {

	@Internal
	public void call(Action<ConvertDataFileTask> cons) {
		cons.execute(this);
		getJsonConverter().convention(getProject().getObjects().newInstance(JsonConverter.class));
		getConverters().convention(getProject().getObjects().newInstance(Converters.class));
	}

	/**
	 * Output Directory
	 */
	@InputDirectory
	public abstract DirectoryProperty getDirectory();

	/** file filter */
	@Input
	@Optional
	public Predicate<File> fileFilter;

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
	public abstract Property<String> getCsvEncoding();

	@Nested
	public abstract Property<JsonConverter> getJsonConverter();

	public void jsonConverter(Action<? super Property<JsonConverter>> action) {
		action.execute(getJsonConverter());
	}

	@Input
	@Optional
	public abstract Property<Boolean> getRecursive();

	@Input
	@Optional
	public abstract Property<String> getSheetName();

	/**
	 * Output File Type
	 */
	@Input
	@Optional
	public abstract Property<String> getOutputFileType();

	@Input
	@Optional
	public abstract Property<Converters> getConverters();

	public void converters(Action<? super Property<Converters>> action) {
		action.execute(getConverters());
	}

	@Input
	@Optional
	public abstract Property<Boolean> getRemoveOriginalFile();

	/**
	 * Output Directory
	 */
	@InputDirectory
	@Optional
	public abstract DirectoryProperty getOutputDirectory();

	@TaskAction
	public void exec() {
		final ConvertDataFileCommand command = new ConvertDataFileCommand();
		initialize(command);
		run(command);
	}

	protected void initialize(ConvertDataFileCommand command) {
		command.setDirectory(this.getDirectory().getAsFile().get());
		if (this.getFileFilter() != null) {
			command.setFileFilter(this.getFileFilter());
		}
		if (this.getCsvEncoding().isPresent()) {
			command.setCsvEncoding(this.getCsvEncoding().get());
		}
		if (this.getJsonConverter().isPresent()) {
			command.setJsonConverter(this.getJsonConverter().get());
		}
		if (this.getRecursive().isPresent()) {
			command.setRecursive(this.getRecursive().get());
		}
		if (this.getSheetName().isPresent()) {
			command.setSheetName(this.getSheetName().get());
		}
		if (this.getOutputFileType().isPresent()) {
			command.setOutputFileType(WorkbookFileType.parse(this.getOutputFileType().get()));
		}
		if (this.getConverters().isPresent()) {
			command.setConverters(this.getConverters().get());
		}
		if (this.getRemoveOriginalFile().isPresent()) {
			command.setRemoveOriginalFile(this.getRemoveOriginalFile().get());
		}
		if (this.getOutputDirectory().isPresent()) {
			command.setOutputDirectory(this.getOutputDirectory().get().getAsFile());
		}
	}

}