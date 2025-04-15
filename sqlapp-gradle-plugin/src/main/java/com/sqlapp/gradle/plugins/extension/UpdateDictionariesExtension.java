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

import java.util.function.Predicate;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.html.UpdateDictionariesCommand;
import com.sqlapp.gradle.plugins.properties.DictionaryFileDirectoryTaskProperty;
import com.sqlapp.gradle.plugins.properties.DictionaryFileTypeTaskProperty;
import com.sqlapp.gradle.plugins.properties.TargetFileTaskProperty;

public abstract class UpdateDictionariesExtension extends AbstractSchemaFileExtension
		implements DictionaryFileDirectoryTaskProperty, DictionaryFileTypeTaskProperty, TargetFileTaskProperty {
	@Inject
	public UpdateDictionariesExtension(Project project) {
		super(project);
	}

	public void call(Action<UpdateDictionariesExtension> cons) {
		cons.execute(this);
	}

	@Input
	@Optional
	private Predicate<String> withSchema;

	public Predicate<String> getWithSchema() {
		return withSchema;
	}

	public void setWithSchema(Predicate<String> withSchema) {
		this.withSchema = withSchema;
	}

	public void withSchema(Predicate<String> withSchema) {
		this.withSchema = withSchema;
	}

	@Input
	@Optional
	public abstract Property<Boolean> getOutputRemarksAsDisplayName();

	@Internal
	@Override
	public void initializeCommand(AbstractCommand command) {
		super.initializeCommand(command);
		if (command instanceof UpdateDictionariesCommand) {
			UpdateDictionariesCommand com = (UpdateDictionariesCommand) command;
			if (getWithSchema() != null) {
				com.setWithSchema(getWithSchema());
			}
			if (getOutputRemarksAsDisplayName().isPresent()) {
				com.setOutputRemarksAsDisplayName(getOutputRemarksAsDisplayName().get());
			}
		}
	}
}
