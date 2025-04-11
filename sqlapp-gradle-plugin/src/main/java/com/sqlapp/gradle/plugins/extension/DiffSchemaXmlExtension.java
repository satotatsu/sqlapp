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
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.DiffCommand;
import com.sqlapp.data.schemas.EqualsHandler;

public abstract class DiffSchemaXmlExtension extends AbstractExtension implements TaskExtension {
	@Inject
	public DiffSchemaXmlExtension(Project project) {
		super(project);
	}

	@Internal
	public void call(Action<DiffSchemaXmlExtension> cons) {
		cons.execute(this);
	}

	/**
	 * Output originalFilePath
	 */
	@InputFile
	public abstract RegularFileProperty getOriginalFile();

	/**
	 * Output targetFilePath
	 */
	@InputFile
	public abstract RegularFileProperty getTargetFile();

	@Input
	@Optional
	public abstract Property<EqualsHandler> getEqualsHandler();

	public void equalsHandler(Action<? super EqualsHandler> action) {
		action.execute(getEqualsHandler().get());
	}

	@Internal
	@Override
	public void setCommand(AbstractCommand command) {
		super.setCommand(command);
		setCommandForTask(command);
		if (command instanceof DiffCommand) {
			DiffCommand com = (DiffCommand) command;
			com.setOriginalFile(getOriginalFile().getAsFile().get());
			com.setTargetFile(getTargetFile().getAsFile().get());
			if (getEqualsHandler().isPresent()) {
				com.setEqualsHandler(this.getEqualsHandler().get());
			}
		}
	}
}