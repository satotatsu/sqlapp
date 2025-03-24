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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;

import com.sqlapp.gradle.plugins.extension.DataSourceExtension;
import com.sqlapp.jdbc.JdbcUtils;
import com.sqlapp.jdbc.SqlappDataSource;
import com.sqlapp.util.MapUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import groovy.util.ConfigObject;
import groovy.util.ConfigSlurper;
import groovy.yaml.YamlSlurper;

public abstract class AbstractDbTask extends AbstractTask {

	@Internal
	@SuppressWarnings("unchecked")
	public HikariConfig getPoolConfiguration(DataSourceExtension obj) {
		HikariConfig poolConfiguration = new HikariConfig();
		ConfigObject configObject = new ConfigObject();
		if (!obj.getProperties().isEmpty()) {
			for (File file : obj.getProperties().getFiles()) {
				if (!file.exists()) {
					continue;
				}
				if (!file.isDirectory()) {
					String lowerName = file.getAbsolutePath().toLowerCase();
					if (lowerName.endsWith(".properties") || lowerName.endsWith(".xml")) {
						Properties prop = readAsProperties(file, lowerName);
						ConfigSlurper slurper = new ConfigSlurper();
						configObject.merge(slurper.parse(prop));
					} else if (lowerName.endsWith(".yaml") || lowerName.endsWith(".yml")) {
						YamlSlurper slurper = new YamlSlurper();
						try {
							MapUtils.merge(configObject, (Map<?, ?>) slurper.parse(file));
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
					}
				}
			}
		}
		obj.setConfig(poolConfiguration);
		return poolConfiguration;
	}

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

	@Internal
	private Properties readAsProperties(File file, String lowerName) {
		Properties prop = new Properties();
		try (InputStream is = new FileInputStream(file)) {
			if (lowerName.endsWith(".xml")) {
				prop.loadFromXML(is);
			} else {
				prop.load(is);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return prop;
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
			final DataSource ds = new HikariDataSource(getPoolConfiguration(obj));
			return ds;
		} else {
			final SqlappDataSource sds = new SqlappDataSource(new HikariDataSource(getPoolConfiguration(obj)));
			sds.setDebug(true);
			return sds;
		}
	}
}
