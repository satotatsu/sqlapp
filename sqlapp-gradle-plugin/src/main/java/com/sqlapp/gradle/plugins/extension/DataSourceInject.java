package com.sqlapp.gradle.plugins.extension;

import org.gradle.api.Action;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;

import com.zaxxer.hikari.HikariConfig;

public interface DataSourceInject {
	@Nested
	DataSourceExtension getDataSource();

	void setDataSource(DataSourceExtension value);

	public default void dataSource(Action<DataSourceExtension> action) {
		action.execute(getDataSource());
	}

	@Internal
	public default HikariConfig getConfig() {
		return getDataSource().toConfig();
	}

}