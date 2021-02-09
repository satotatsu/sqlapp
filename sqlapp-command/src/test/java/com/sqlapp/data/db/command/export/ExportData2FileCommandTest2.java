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
/**
 * This file is part of sqlapp.
 *
 * sqlapp is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.command.export;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.ParseException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.command.version.DbVersionHandler;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.JdbcUtils;
import com.sqlapp.jdbc.SqlappDataSource;
import com.sqlapp.test.AbstractTest;
import com.sqlapp.util.CommonUtils;
import com.zaxxer.hikari.HikariConfig;

public class ExportData2FileCommandTest2 extends AbstractTest {
	/**
	 * JDBC URL
	 */
	protected String url;
	
	private String username;
	private String password;
	
	public ExportData2FileCommandTest2(){
		url=getTestProp("jdbc.url");
		username=getTestProp("jdbc.username");
		password=getTestProp("jdbc.password");
	}
	
	private final String directoryPath="./bin/export";
	
	
	
	@Test
	public void testRun() throws ParseException, IOException, SQLException {
		if (CommonUtils.isEmpty(this.getUrl())){
			return;
		}
		final ExportData2FileCommand command=new ExportData2FileCommand();
		final DataSource dataSource=newDataSource();
		//command.setIncludeTables("*");
		command.setDataSource(dataSource);
		command.setDirectory(new File(directoryPath));
		command.setUseSchemaNameDirectory(true);
		command.setOnlyCurrentSchema(false);
		command.setDefaultExport(true);
		//
		final DbVersionHandler handler=new DbVersionHandler();
		final Table table=handler.createVersionTableDefinition("test");
		try(Connection connection=dataSource.getConnection()){
			final Dialect dialect=DialectResolver.getInstance().getDialect(connection);
			handler.createTable(connection, dialect, table);
		}
		//command.run();
	}
	
	protected HikariConfig getPoolConfiguration() {
		final HikariConfig poolConfiguration = new HikariConfig();
		poolConfiguration.setJdbcUrl(this.getUrl());
		poolConfiguration.setDriverClassName(JdbcUtils.getDriverClassNameByUrl(this.getUrl()));
		poolConfiguration.setUsername(this.getUsername());
		poolConfiguration.setPassword(this.getPassword());
		return poolConfiguration;
	}

	protected DataSource newDataSource() {
		final DataSource ds = new SqlappDataSource(
					new com.zaxxer.hikari.HikariDataSource(
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
	 * @return the username
	 */
	public String getUsername() {
		return username;
	}

	/**
	 * @param username the username to set
	 */
	public void setUsername(final String username) {
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
	public void setPassword(final String password) {
		this.password = password;
	}

	/**
	 * @param url the url to set
	 */
	public void setUrl(final String url) {
		this.url = url;
	}

}
