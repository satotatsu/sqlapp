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

import javax.sql.DataSource;

import org.gradle.api.Action;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;

import com.sqlapp.jdbc.SqlappDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

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

	@Internal
	public default DataSource createDataSource(boolean debug) {
		HikariConfig config = this.getConfig();
		if (!debug) {
			final DataSource ds = new HikariDataSource(config);
			return ds;
		} else {
			final SqlappDataSource sds = new SqlappDataSource(new HikariDataSource(config));
			sds.setDebug(debug);
			return sds;
		}
	}
}