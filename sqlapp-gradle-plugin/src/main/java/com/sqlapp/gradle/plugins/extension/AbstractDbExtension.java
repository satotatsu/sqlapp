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

public abstract class AbstractDbExtension extends AbstractExtension implements DataSourceInject {

	@Inject
	protected AbstractDbExtension(Project project) {
		super(project);
		this.setDataSource(this.getProject().getObjects().newInstance((DataSourceExtension.class)));
	}

	@Internal
	@Override
	public void setCommand(AbstractCommand command, boolean debug) {
		super.setCommand(command, debug);
		if (command instanceof AbstractDataSourceCommand) {
			AbstractDataSourceCommand com = (AbstractDataSourceCommand) command;
			final HikariConfig config = getConfig();
			com.setDataSource(createDataSource(config, debug));
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
