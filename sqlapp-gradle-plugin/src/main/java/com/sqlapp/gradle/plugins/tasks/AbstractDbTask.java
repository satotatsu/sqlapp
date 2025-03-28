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

package com.sqlapp.gradle.plugins.tasks;

import javax.sql.DataSource;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;

import com.sqlapp.gradle.plugins.extension.DataSourceExtension;
import com.sqlapp.jdbc.JdbcUtils;
import com.sqlapp.jdbc.SqlappDataSource;
import com.zaxxer.hikari.HikariDataSource;

public abstract class AbstractDbTask extends AbstractTask {

	/**
	 * @return the driverClassName
	 */
	@Internal
	protected String getDriverClassName(String driverClassName, String url) {
		if (driverClassName == null) {
			driverClassName = JdbcUtils.getDriverClassNameByUrl(url);
		}
		return driverClassName;
	}

	/**
	 * @return the dataSource
	 */
	@Internal
	protected DataSource createDataSource(Property<DataSourceExtension> prop) {
		return createDataSource(prop.get());
	}

	/**
	 * @return the dataSource
	 */
	@Internal
	protected DataSource createDataSource(DataSourceExtension obj) {
		boolean debug = getDebug().getOrElse(false);
		if (!debug) {
			final DataSource ds = new HikariDataSource(obj.toConfig());
			return ds;
		} else {
			final SqlappDataSource sds = new SqlappDataSource(new HikariDataSource(obj.toConfig()));
			sds.setDebug(true);
			return sds;
		}
	}
}
