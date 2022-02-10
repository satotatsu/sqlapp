/*
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-gradle-plugin.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.gradle.plugins

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.apache.tomcat.jdbc.pool.PoolProperties;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.gradle.plugins.pojo.DataSourcePojo
import com.sqlapp.jdbc.JdbcUtils;
import com.sqlapp.jdbc.SqlappDataSource;
import com.sqlapp.util.SimpleBeanUtils;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

abstract class AbstractDbTask extends AbstractTask{

	protected PoolConfiguration getPoolConfiguration(DataSourcePojo obj) {
		PoolConfiguration poolConfiguration = new PoolProperties();
		List<File> files=this.getFiles(obj.getProperties());
		ConfigSlurper slurper = new ConfigSlurper();
		ConfigObject configObject=new ConfigObject();
		files.forEach({File file->
			if (!file.exists()){
				return;
			}
			if (!file.isDirectory()){
				if (file.getAbsolutePath().endsWith(".properties")){
					Properties prop = new Properties()
					new FileInputStream(file).withCloseable{
						prop.load(it);
					}
					configObject.merge(slurper.parse(prop));
				} else if (file.getAbsolutePath().endsWith(".xml")){
					Properties prop = new Properties()
					new FileInputStream(file).withCloseable{
						prop.loadFromXML(it);
					}
					configObject.merge(slurper.parse(prop));
				}
			}
		});
		configObject.each ({k,v->
			if (v!=null&&!"".equals(v)) {
				SimpleBeanUtils.setValue(poolConfiguration, k, v);
			}
		});
		poolConfiguration.setDriverClassName(getDriverClassName(obj.driverClassName, obj.url));
		if (obj.getUrl()!=null) {
			poolConfiguration.setUrl(obj.getUrl());
		}
		if (obj.getUsername()!=null) {
			poolConfiguration.setUsername(obj.getUsername());
		}
		if (obj.getPassword()!=null) {
			poolConfiguration.setPassword(obj.getPassword());
		}
		if (obj.getDefaultTransactionIsolation()!=null) {
			poolConfiguration.setDefaultTransactionIsolation(obj.getDefaultTransactionIsolation());
		}
		if (obj.getDefaultAutoCommit()!=null) {
			poolConfiguration.setDefaultAutoCommit(obj.getDefaultAutoCommit());
		}
		if (obj.getDefaultCatalog()!=null) {
			poolConfiguration.setDefaultCatalog(obj.getDefaultCatalog());
		}
		if (obj.getFairQueue()!=null) {
			poolConfiguration.setFairQueue(obj.getFairQueue());
		}
		if (obj.getInitialSize()!=null) {
			poolConfiguration.setInitialSize(obj.getInitialSize());
		} else {
			poolConfiguration.setInitialSize(0);
		}
		if (obj.getInitSQL()!=null) {
			poolConfiguration.setInitSQL(obj.getInitSQL());
		}
		if (obj.getJmxEnabled()!=null) {
			poolConfiguration.setJmxEnabled(obj.getJmxEnabled());
		}
		if (obj.getMaxActive()!=null) {
			poolConfiguration.setMaxActive(obj.getMaxActive());
		}
		if (obj.getMaxIdle()!=null) {
			poolConfiguration.setMaxIdle(obj.getMaxIdle());
		}
		if (obj.getMaxAge()!=null) {
			poolConfiguration.setMaxAge(obj.getMaxAge())
		}
		if (obj.getMaxWait()!=null) {
			poolConfiguration.setMaxWait(obj.getMaxWait());
		}
		if (obj.getMinEvictableIdleTimeMillis()!=null) {
			poolConfiguration.setMinEvictableIdleTimeMillis(obj.getMinEvictableIdleTimeMillis());
		}
		if (obj.getMinIdle()!=null) {
			poolConfiguration.setMinIdle(obj.getMinIdle());
		}
		if (obj.getName()!=null) {
			poolConfiguration.setName(obj.getName());
		}
		if (obj.getTestOnBorrow()!=null) {
			poolConfiguration.setTestOnBorrow(obj.getTestOnBorrow());
		}
		if (obj.getTestOnConnect()!=null) {
			poolConfiguration.setTestOnConnect(obj.getTestOnConnect());
		}
		if (obj.getTestOnReturn()!=null) {
			poolConfiguration.setTestOnReturn(obj.getTestOnReturn());
		}
		if (obj.getTestWhileIdle()!=null) {
			poolConfiguration.setTestWhileIdle(obj.getTestWhileIdle());
		}
		if (obj.getValidationInterval()!=null) {
			poolConfiguration.setValidationInterval(obj.getValidationInterval());
		}
		if (obj.getValidationQueryTimeout()!=null) {
			poolConfiguration.setValidationQueryTimeout(obj.getValidationQueryTimeout());
		}
		if (obj.getValidationQuery()!=null) {
			poolConfiguration.setValidationQuery(obj.getValidationQuery());
		}
		return poolConfiguration;
	}

	protected DataSource newDataSource(def obj) {
		DataSource ds = null;
		if (obj instanceof DataSource){
			ds=obj;
		}
		if (!isDebug()) {
			ds = new org.apache.tomcat.jdbc.pool.DataSource(
					getPoolConfiguration(obj));
		} else {
			if (ds!=null){
				if (ds instanceof SqlappDataSource){
					SqlappDataSource sds =(SqlappDataSource)ds;
					sds.setDebug(isDebug());
					return ds;
				}
				SqlappDataSource sds =new SqlappDataSource(ds);
				sds.setDebug(isDebug());
				ds=sds;
			} else{
				if (!isDebug()) {
					ds = new org.apache.tomcat.jdbc.pool.DataSource(
							getPoolConfiguration(obj));
				} else{
					SqlappDataSource sds = new SqlappDataSource(
							new org.apache.tomcat.jdbc.pool.DataSource(
							getPoolConfiguration(obj)));
					sds.setDebug(isDebug());
					ds=sds;
				}
			}
		}
		return ds;
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
	protected DataSource createDataSource() {
		if (this.pojo!=null){
			Object ds=pojo.dataSource;
			if (ds instanceof DataSource){
				return (DataSource)ds;
			} else{
				return newDataSource(ds);
			}
		}
		return null;
	}
}
