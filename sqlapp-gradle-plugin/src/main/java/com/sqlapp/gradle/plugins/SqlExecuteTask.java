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

import org.gradle.api.Action;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.SqlExecuteCommand;
import com.sqlapp.gradle.plugins.properties.DataSourceTaskProperty;
import com.sqlapp.gradle.plugins.properties.EncodingTaskProperty;
import com.sqlapp.gradle.plugins.properties.PlaceholderTaskProperty;

@DisableCachingByDefault
public abstract class SqlExecuteTask extends AbstractDirectoryTask<SqlExecuteCommand>
		implements DataSourceTaskProperty, EncodingTaskProperty, PlaceholderTaskProperty {

	public void call(Action<SqlExecuteTask> cons) {
		cons.execute(this);
	}

	@Input
	@Optional
	public abstract Property<String> getSqlText();

	@Override
	protected SqlExecuteCommand createCommand() {
		return new SqlExecuteCommand();
	}

	@Override
	protected void beforeRun(SqlExecuteCommand command) {
		super.beforeRun(command);
		if (getSqlText().isPresent()) {
			command.setSqlText(getSqlText().get());
		}
	}
}