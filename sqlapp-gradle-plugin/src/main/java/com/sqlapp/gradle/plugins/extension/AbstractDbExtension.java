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
import javax.sql.DataSource;

import org.gradle.api.Project;
import org.gradle.api.tasks.Internal;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.AbstractDataSourceCommand;
import com.sqlapp.jdbc.SqlappDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public abstract class AbstractDbExtension extends AbstractExtension implements DataSourceInject, TaskExtension {

	@Inject
	protected AbstractDbExtension(Project project) {
		super(project);
		this.setDataSource(this.getProject().getObjects().newInstance((DataSourceExtension.class)));
	}

	@Internal
	@Override
	public void setCommand(AbstractCommand command) {
		super.setCommand(command);
		setCommandForTask(command);
		if (command instanceof AbstractDataSourceCommand) {
			AbstractDataSourceCommand com = (AbstractDataSourceCommand) command;
			final HikariConfig config = getConfig();
			com.setDataSource(createDataSource(config, this.getDebug().getOrElse(false)));
		}
	}

	@Internal
	private DataSource createDataSource(HikariConfig config, boolean debug) {
		if (!debug) {
			DataSource ds = new HikariDataSource(config);
			return ds;
		} else {
			SqlappDataSource sds = new SqlappDataSource(new HikariDataSource(config));
			sds.setDebug(debug);
			return sds;
		}
	}
}
