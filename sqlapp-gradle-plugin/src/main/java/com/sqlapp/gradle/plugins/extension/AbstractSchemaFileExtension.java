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
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.html.AbstractSchemaFileCommand;
import com.sqlapp.util.JsonConverter;

/**
 * Schema用のExtension
 */
public abstract class AbstractSchemaFileExtension extends AbstractDbExtension {
	@Inject
	protected AbstractSchemaFileExtension(Project project) {
		super(project);
		targetFile = project.getObjects().fileProperty();
	}

	private final RegularFileProperty targetFile;

	@InputFile
	public RegularFileProperty getTargetFile() {
		return targetFile;
	}

	@Input
	@Optional
	public abstract DirectoryProperty getDictionaryFileDirectory();

	@Input
	@Optional
	public abstract Property<String> getDictionaryFileType();

	@Input
	@Optional
	public abstract Property<String> getCsvEncoding();

	@Nested
	public abstract Property<JsonConverter> getJsonConverter();

	public void jsonConverter(Action<? super JsonConverter> action) {
		if (getJsonConverter().isPresent()) {
			action.execute(getJsonConverter().get());
		}
	}

	@Internal
	@Override
	public void setCommand(AbstractCommand command) {
		super.setCommand(command);
		if (command instanceof AbstractSchemaFileCommand) {
			AbstractSchemaFileCommand com = (AbstractSchemaFileCommand) command;
			com.setTargetFile(getTargetFile().getAsFile().get());
			if (getDictionaryFileDirectory().isPresent()) {
				com.setDictionaryFileDirectory(getDictionaryFileDirectory().getAsFile().get());
			}
			if (getDictionaryFileType().isPresent()) {
				com.setDictionaryFileType(getDictionaryFileType().get());
			}
			if (getCsvEncoding().isPresent()) {
				com.setCsvEncoding(getCsvEncoding().get());
			}
			if (getJsonConverter().isPresent()) {
				com.setJsonConverter(getJsonConverter().get());
			}
		}
	}
}
