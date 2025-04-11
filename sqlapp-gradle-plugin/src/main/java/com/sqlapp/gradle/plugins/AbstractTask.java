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
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.ConsoleOutputLevel;

public abstract class AbstractTask extends DefaultTask {

	@Input
	@Optional
	public abstract Property<Boolean> getDebug();

	@Input
	@Optional
	public abstract MapProperty<String, Object> getParameters();

	@Input
	@Optional
	public abstract Property<String> getConsoleOutputLevel();

	@Internal
	protected void run(AbstractCommand command) {
		if (this.getParameters().isPresent()) {
			command.getContext().putAll(this.getParameters().get());
		}
		if (getDebug().getOrElse(false)) {
			System.out.println("parameters=" + this.getParameters().get());
		}
		if (getConsoleOutputLevel().isPresent()) {
			command.setConsoleOutputLevel(ConsoleOutputLevel.parse(getConsoleOutputLevel().get()));
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
