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
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Internal;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.gradle.plugins.extension.AbstractExtension;
import com.sqlapp.gradle.plugins.properties.TaskPropertiesEnum;

@DisableCachingByDefault
public abstract class AbstractExtensionTask<T extends AbstractCommand, S> extends AbstractTask<T> {

	@Classpath
	public abstract ConfigurableFileCollection getRuntimeClasspath();

	@Inject
	public AbstractExtensionTask() {
		TaskPropertiesEnum.initializeAll(getProject().getObjects(), this);
		this.extension = createExtension(getProject());
		if (this.extension instanceof AbstractExtension) {
			final AbstractExtension ext = (AbstractExtension) extension;
			if (ext.getEnable().isPresent()) {
				this.setEnabled(ext.getEnable().get());
			}
		}
	}

	private S extension;

	protected abstract S createExtension(Project project);

	@Internal
	protected S getExtension() {
		return this.extension;
	}

	@Override
	protected void initializeCommand(T command) {
		TaskPropertiesEnum.setAllProperties(extension, command);
		final AbstractExtension ext = (AbstractExtension) extension;
		ext.initializeCommand(command);
	}
}
