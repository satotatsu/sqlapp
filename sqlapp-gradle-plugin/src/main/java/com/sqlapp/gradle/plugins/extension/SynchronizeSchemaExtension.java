/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.SynchronizeSchemaCommand;
import com.sqlapp.data.db.sql.SqlExecutor;
import com.sqlapp.data.schemas.EqualsHandler;

public abstract class SynchronizeSchemaExtension extends AbstractSchemaFileExtension {
	@Inject
	public SynchronizeSchemaExtension(Project project) {
		super(project);
	}

	@Internal
	public void call(Action<SynchronizeSchemaExtension> cons) {
		cons.execute(this);
	}

	@Input
	@Optional
	public abstract Property<EqualsHandler> getEqualsHandler();

	public void equalsHandler(Action<? super Property<EqualsHandler>> action) {
		action.execute(getEqualsHandler());
	}

	@InputFile
	@Optional
	public abstract ConfigurableFileCollection getFiles();

	@Input
	@Optional
	public abstract Property<SqlExecutor> getSqlExecutor();

	public void sqlExecutor(Action<? super SqlExecutor> action) {
		if (getSqlExecutor().isPresent()) {
			action.execute(getSqlExecutor().get());
		}
	}

	@Internal
	@Override
	public void setCommand(AbstractCommand command, boolean debug) {
		super.setCommand(command, debug);
		if (command instanceof SynchronizeSchemaCommand) {
			SynchronizeSchemaCommand com = (SynchronizeSchemaCommand) command;
			if (getEqualsHandler().isPresent()) {
				com.setEqualsHandler(getEqualsHandler().get());
			}
			if (getFiles().isEmpty()) {
				com.setFiles(getFiles().getFiles().toArray(new File[0]));
			}
			if (getSqlExecutor().isPresent()) {
				com.setSqlExecutor(getSqlExecutor().get());
			}
		}
	}
}
