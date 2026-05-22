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

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.gradle.plugins.extension.AbstractExtension;
import com.sqlapp.gradle.plugins.properties.ConsoleOutputLevelTaskProperty;
import com.sqlapp.gradle.plugins.properties.ContextTaskProperty;
import com.sqlapp.gradle.plugins.properties.DebugTaskProperty;
import com.sqlapp.gradle.plugins.properties.TaskPropertiesEnum;

@DisableCachingByDefault
public abstract class AbstractTask<T extends AbstractCommand, S> extends DefaultTask
		implements DebugTaskProperty, ContextTaskProperty, ConsoleOutputLevelTaskProperty {

	public AbstractTask() {
		TaskPropertiesEnum.initializeAll(getProject(), this);
		this.command = createCommand();
		this.extension = createExtension(getProject());
		if (this.extension instanceof AbstractExtension) {
			final AbstractExtension ext = (AbstractExtension) extension;
			if (ext.getEnable().isPresent()) {
				this.setEnabled(ext.getEnable().get());
			}
		}
	}

	private T command;
	private S extension;

	@TaskAction
	public void exec() {
		if (extension != null) {
			TaskPropertiesEnum.setAllProperties(extension, command);
			final AbstractExtension ext = (AbstractExtension) extension;
			ext.initializeCommand(command);
		} else {
			TaskPropertiesEnum.setAllProperties(this, command);
		}
		exec(command, extension);
	}

	protected abstract T createCommand();

	protected abstract S createExtension(Project project);

	protected abstract void exec(T command, S extension);

	protected void run(AbstractCommand command) {
		if (this.extension == null) {
			// Extensionがない場合は自分自身のを使用する
			TaskPropertiesEnum.setDebugProperties(this, command);
		}
		if (this.getEnabled()) {
			try {
				command.run();
			} catch (Exception e) {
				e.printStackTrace();
				throw e;
			}
		} else {
			System.out.println("This task is disabled.");
		}
	}

}
