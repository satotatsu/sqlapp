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

import java.util.Map;

import javax.sql.DataSource;

import org.gradle.api.Project;
import org.gradle.api.tasks.Internal;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.properties.ContextProperty;
import com.sqlapp.data.db.command.properties.DataSourceProperty;
import com.sqlapp.gradle.plugins.extension.AbstractExtension;
import com.sqlapp.gradle.plugins.properties.TaskPropertiesEnum;
import com.sqlapp.jdbc.SqlappDataSource;

@DisableCachingByDefault
public abstract class AbstractExtensionTask<T extends AbstractCommand, S> extends AbstractTask<T> {

	public AbstractExtensionTask() {
		this.extension = createExtension(getProject());
		if (this.extension instanceof AbstractExtension) {
			final AbstractExtension ext = (AbstractExtension) extension;
			// Evaluated at execution time, so the extension can be configured after
			// the task has been created.
			onlyIf("the extension's enable property is not false", t -> ext.getEnable().getOrElse(true));
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
		applyTaskOverrides(command);
	}

	/**
	 * Applies debug, consoleOutputLevel and parameters set on the task itself on
	 * top of the shared extension.
	 *
	 * @param command the command
	 */
	protected void applyTaskOverrides(T command) {
		TaskPropertiesEnum.CONSOLE_OUTPUT_LEVEL.setProperty(this, command);
		final Map<String, Object> parameters = getParameters().getOrElse(Map.of());
		if (!parameters.isEmpty() && command instanceof ContextProperty) {
			final Map<String, Object> context = ((ContextProperty) command).getContext();
			if (context != null) {
				context.putAll(parameters);
			}
		}
		if (getDebug().getOrElse(false)) {
			TaskPropertiesEnum.DEBUG.setProperty(this, command);
			if (command instanceof DataSourceProperty) {
				final DataSourceProperty prop = (DataSourceProperty) command;
				final DataSource ds = prop.getDataSource();
				if (ds != null && !(ds instanceof SqlappDataSource)) {
					// Same wrapping as TaskPropertiesEnum.DATA_SOURCE for debug.
					final SqlappDataSource sds = new SqlappDataSource(ds);
					sds.setDebug(false);
					prop.setDataSource(sds);
				}
			}
		}
	}
}
