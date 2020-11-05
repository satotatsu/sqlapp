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
package com.sqlapp.test;

import java.io.File;

import javax.sql.DataSource;

import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.apache.tomcat.jdbc.pool.PoolProperties;

import com.sqlapp.data.db.command.ExportXmlCommand;
import com.sqlapp.jdbc.SqlappDataSource;
/**
 * Export用のテスト
 * @author tatsuo satoh
 *
 */
public abstract class AbstractExportXmlCommandTest extends AbstractTest{

	private String outputFileName="src/test/dump.xml";

	private File outputPath=null;
	//private String outputPath="src/text/dump.xml";
	
	public void test() {
		ExportXmlCommand command=new ExportXmlCommand();
		command.setDataSource(this.newDataSource());
		command.setDumpRows(false);
		command.setOutputFileName(outputFileName);
		command.setOutputPath(outputPath);
		try{
			command.run();			
		} catch(Exception e){
			e.printStackTrace();
		}
	}

	protected PoolConfiguration getPoolConfiguration() {
		PoolConfiguration poolConfiguration = new PoolProperties();
		poolConfiguration.setDriverClassName(this.getDriverClassName());
		poolConfiguration.setUrl(this.getUrl());
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
	 * @return the driverClassName
	 */
	public abstract String getDriverClassName();

	/**
	 * @return the url
	 */
	public abstract String getUrl();

	/**
	 * @return the username
	 */
	public abstract String getUsername();

	/**
	 * @return the password
	 */
	public abstract String getPassword();
	
	
}
