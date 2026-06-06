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

import java.io.IOException;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.GenerateDiffSqlCommand;
import com.sqlapp.data.schemas.DefaultSchemaEqualsHandler;
import com.sqlapp.data.schemas.EqualsHandler;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.gradle.plugins.properties.EqualsHandlerTaskProperty;
import com.sqlapp.gradle.plugins.properties.OriginalFileTaskProperty;

/**
 * Schema用のExtension
 */
public abstract class GenerateDiffSqlExtension extends AbstractGenerateSqlExtension
		implements EqualsHandlerTaskProperty, OriginalFileTaskProperty {
	@Inject
	public GenerateDiffSqlExtension(Project project) {
		super(project);
		this.equalsHandler = new DefaultSchemaEqualsHandler();
	}

	public void call(Action<GenerateDiffSqlExtension> cons) {
		cons.execute(this);
	}

	@Input
	public abstract Property<Boolean> getWithVersionDown();

	private EqualsHandler equalsHandler;

	@Internal
	public EqualsHandler getEqualsHandler() {
		return equalsHandler;
	}

	public void setEqualsHandler(EqualsHandler equalsHandler) {
		this.equalsHandler = equalsHandler;
	}

	@Internal
	@Override
	public void initializeCommand(AbstractCommand command) {
		super.initializeCommand(command);
		if (command instanceof GenerateDiffSqlCommand) {
			GenerateDiffSqlCommand com = (GenerateDiffSqlCommand) command;
			try {
				com.setOriginal(SchemaUtils.readXml(getOriginalFile().get().getAsFile()));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			try {
				com.setTarget(SchemaUtils.readXml(getTargetFile().get().getAsFile()));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}
}
