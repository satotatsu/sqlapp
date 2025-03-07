/*
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.gradle.plugins

import java.sql.Connection;
import java.sql.Wrapper;
import java.sql.SQLException;

import javax.sql.DataSource;

import com.sqlapp.gradle.plugins.pojo.DataSourcePojo
import com.sqlapp.gradle.plugins.pojo.DbPojo
import com.sqlapp.jdbc.JdbcUtils;
import com.sqlapp.jdbc.SqlappDataSource;
import com.sqlapp.util.SimpleBeanUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import groovy.yaml.YamlSlurper;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

abstract class AbstractDbTask extends AbstractTask{

	protected HikariConfig getPoolConfiguration(DataSourcePojo obj) {
		HikariConfig poolConfiguration = new HikariConfig();
		List<File> files=this.getFiles(obj.getProperties());
		ConfigObject configObject=new ConfigObject();
		files.forEach({File file->
			if (!file.exists()){
				return;
			}
			if (!file.isDirectory()){
				String lowerName=file.getAbsolutePath().toLowerCase();
				if (lowerName.endsWith(".properties")){
					Properties prop = new Properties()
					new FileInputStream(file).withCloseable{
						prop.load(it);
					}
					ConfigSlurper slurper = new ConfigSlurper();
					configObject.merge(slurper.parse(prop));
				} else if (lowerName.endsWith(".xml")){
					Properties prop = new Properties()
					new FileInputStream(file).withCloseable{
						prop.loadFromXML(it);
					}
					ConfigSlurper slurper = new ConfigSlurper();
					configObject.merge(slurper.parse(prop));
				} else if (lowerName.endsWith(".yaml")||lowerName.endsWith(".yml")){
					YamlSlurper slurper = new YamlSlurper();
					configObject.merge(slurper.parse(file));
				}
			}
		});
		configObject.each ({k,v->
			if (v!=null&&!"".equals(v)) {
				SimpleBeanUtils.setValue(poolConfiguration, k, v);
			}
		});
		if (obj.getDriverClassName()!=null) {
			poolConfiguration.setDriverClassName(obj.getDriverClassName());
		} else {
			String driverClassName=getDriverClassName(obj.driverClassName, obj.jdbcUrl);
			if (driverClassName!=null) {
				poolConfiguration.setDriverClassName(driverClassName);
			}
		}
		if (obj.getJdbcUrl()!=null) {
			poolConfiguration.setJdbcUrl(obj.getJdbcUrl());
		}
		if (obj.getUsername()!=null) {
			poolConfiguration.setUsername(obj.getUsername());
		}
		if (obj.getPassword()!=null) {
			poolConfiguration.setPassword(obj.getPassword());
		}
		if (obj.getTransactionIsolation()!=null) {
			poolConfiguration.setTransactionIsolation(obj.getTransactionIsolation());
		}
		if (obj.getAutoCommit()!=null) {
			poolConfiguration.setAutoCommit(obj.getAutoCommit());
		}
		if (obj.getAllowPoolSuspension()!=null) {
			poolConfiguration.setAllowPoolSuspension(obj.getAllowPoolSuspension());
		}
		if (obj.getCatalog()!=null) {
			poolConfiguration.setCatalog(obj.getCatalog());
		}
		if (obj.getConnectionTimeout()!=null) {
			poolConfiguration.setConnectionTimeout(obj.getConnectionTimeout());
		}
		if (obj.getValidationTimeout()!=null) {
			poolConfiguration.setValidationTimeout(obj.getValidationTimeout());
		}
		if (obj.getIdleTimeout()!=null) {
			poolConfiguration.setIdleTimeout(obj.getIdleTimeout());
		}
		if (obj.getLeakDetectionThreshold()!=null) {
			poolConfiguration.setLeakDetectionThreshold(obj.getLeakDetectionThreshold());
		}
		if (obj.getMaxLifetime()!=null) {
			poolConfiguration.setMaxLifetime(obj.getMaxLifetime());
		}
		if (obj.getMinimumIdle()!=null) {
			poolConfiguration.setMinimumIdle(obj.getMinimumIdle());
		} else {
			poolConfiguration.setMinimumIdle(0);
		}
		if (obj.getMaximumPoolSize()!=null) {
			poolConfiguration.setMaximumPoolSize(obj.getMaximumPoolSize());
		}
		if (obj.getKeepaliveTime()!=null) {
			poolConfiguration.setKeepaliveTime(obj.getKeepaliveTime());
		}
		if (obj.getPoolName()!=null) {
			poolConfiguration.setPoolName(obj.getPoolName());
		}
		if (obj.getConnectionInitSql()!=null) {
			poolConfiguration.setConnectionInitSql(obj.getConnectionInitSql());
		}
		if (obj.getConnectionTestQuery()!=null) {
			poolConfiguration.setConnectionTestQuery(obj.getConnectionTestQuery());
		}
		if (obj.getConnectionTimeout()!=null) {
			poolConfiguration.setConnectionTimeout(obj.getConnectionTimeout());
		}
		return poolConfiguration;
	}

	/**
	 * @return the driverClassName
	 */
	protected String getDriverClassName(String driverClassName, String url) {
		if (driverClassName == null) {
			driverClassName = JdbcUtils.getDriverClassNameByUrl(url);
		}
		return driverClassName;
	}
	
	/**
	 * @return the dataSource
	 */
	protected DataSource createDataSource(DbPojo obj) {
		DataSource ds;
		if (obj.debug) {
			if (obj.dataSourceImpl!=null) {
				ds=obj.dataSourceImpl;
			} else {
				ds = new HikariDataSource(getPoolConfiguration(obj.dataSource));
			}
		} else{
			SqlappDataSource sds;
			if (obj.dataSourceImpl!=null) {
				sds = new SqlappDataSource(obj.dataSourceImpl);
			} else {
				sds = new SqlappDataSource(
					new HikariDataSource(getPoolConfiguration(obj.dataSource)));
			}
			sds.setDebug(debug);
			ds=sds;
		}
		return ds;
	}
}
