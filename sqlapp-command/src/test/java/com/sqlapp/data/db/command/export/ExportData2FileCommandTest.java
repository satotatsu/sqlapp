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
package com.sqlapp.data.db.command.export;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.ParseException;

import javax.sql.DataSource;

import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.command.export.ExportData2FileCommand;
import com.sqlapp.data.db.command.version.DbVersionHandler;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.JdbcUtils;
import com.sqlapp.jdbc.SqlappDataSource;
import com.sqlapp.test.AbstractTest;
import com.sqlapp.util.CommonUtils;

public class ExportData2FileCommandTest extends AbstractTest {
	/**
	 * JDBC URL
	 */
	protected String url="jdbc:hsqldb:.";
	
	private String directoryPath="./src/test/temp/export";
	
	@Test
	public void testRun() throws ParseException, IOException, SQLException {
		if (CommonUtils.isEmpty(this.getUrl())){
			return;
		}
		ExportData2FileCommand command=new ExportData2FileCommand();
		DataSource dataSource=newDataSource();
		//command.setIncludeTables("*");
		command.setDataSource(dataSource);
		command.setDirectory(new File(directoryPath));
		command.setUseSchemaNameDirectory(true);
		command.setOnlyCurrentSchema(false);
		command.setDefaultExport(true);
		//
		DbVersionHandler handler=new DbVersionHandler();
		Table table=handler.createVersionTableDefinition("test");
		try(Connection connection=dataSource.getConnection()){
			Dialect dialect=DialectResolver.getInstance().getDialect(connection);
			handler.createTable(connection, dialect, table);
		}
		//command.run();
	}
	
	protected PoolConfiguration getPoolConfiguration() {
		PoolConfiguration poolConfiguration = new PoolProperties();
		poolConfiguration.setUrl(this.getUrl());
		poolConfiguration.setDriverClassName(JdbcUtils.getDriverClassNameByUrl(this.getUrl()));
		//poolConfiguration.setUsername(this.getUsername());
		//poolConfiguration.setPassword(this.getPassword());
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

}
