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

import javax.inject.Inject;

import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.DiffCommand;
import com.sqlapp.gradle.plugins.extension.DiffSchemaXmlExtension;

@DisableCachingByDefault
public abstract class DiffSchemaXmlTask extends AbstractTask<DiffCommand, DiffSchemaXmlExtension> {
	@Inject
	public DiffSchemaXmlTask(ObjectFactory objectFactory) {
		super(objectFactory);
	}

	@Override
	protected DiffCommand createCommand() {
		return new DiffCommand();
	}

	@Override
	protected DiffSchemaXmlExtension createExtension(Project project) {
		final DiffSchemaXmlExtension obj = project.getExtensions().getByType(DiffSchemaXmlExtension.class);
		return obj;
	}

}
