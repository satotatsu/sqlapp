/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.command.version;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;

import javax.sql.DataSource;

import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.junit.jupiter.api.Test;

import com.sqlapp.jdbc.JdbcUtils;
import com.sqlapp.jdbc.SqlappDataSource;
import com.sqlapp.test.AbstractTest;

public class VersionUpCommandDbTest extends AbstractTest {
	/**
	 * JDBC URL
	 */
	protected String url;
	
	private String username;
	private String password;
	
	public VersionUpCommandDbTest(){
		url=getTestProp("jdbc.url");
		username=getTestProp("jdbc.username");
		password=getTestProp("jdbc.password");
	}
	
	@Test
	public void testRun() throws ParseException, IOException, SQLException {
		String suffix="_test";
		VersionUpCommand command=new VersionUpCommand();
		DataSource dataSource=newDataSource();
		command.setSqlDirectory(new File("src/test/resources/migration"));
		command.setSchemaChangeLogTableName("changelog");
		//command.setSchemaChangeLogTableName("master"+suffix+".changelog");
		command.getContext().put("schemaNameSuffix", suffix);
		command.setDataSource(dataSource);
		command.setLastChangeToApply(Long.valueOf("000000000010"));
		command.setShowVersionOnly(true);
		command.run();
	}
	
	protected PoolConfiguration getPoolConfiguration() {
		PoolConfiguration poolConfiguration = new PoolProperties();
		poolConfiguration.setUrl(this.getUrl());
		poolConfiguration.setDriverClassName(JdbcUtils.getDriverClassNameByUrl(this.getUrl()));
		poolConfiguration.setUsername(this.getUsername());
		poolConfiguration.setPassword(this.getPassword());
		return poolConfiguration;
	}

	protected DataSource newDataSource() {
		DataSource ds = new SqlappDataSource(
					new org.apache.tomcat.jdbc.pool.DataSource(
							getPoolConfiguration()));
		return ds;
	}

	/**
	 * @return the url
	 */
	public String getUrl() {
		return url;
	}

	/**
	 * @param url the url to set
	 */
	public void setUrl(String url) {
		this.url = url;
	}

	/**
	 * @return the username
	 */
	public String getUsername() {
		return username;
	}

	/**
	 * @param username the username to set
	 */
	public void setUsername(String username) {
		this.username = username;
	}

	/**
	 * @return the password
	 */
	public String getPassword() {
		return password;
	}

	/**
	 * @param password the password to set
	 */
	public void setPassword(String password) {
		this.password = password;
	}

}
