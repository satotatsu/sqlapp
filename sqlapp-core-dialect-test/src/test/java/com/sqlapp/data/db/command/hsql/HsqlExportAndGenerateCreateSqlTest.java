/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-test.
 *
 * sqlapp-core-test is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-test is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-test.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.command.hsql;

import java.sql.Connection;
import java.sql.SQLException;

import com.sqlapp.data.db.command.AbstractExportAndGenerateCreateSqlTest;
import com.sqlapp.data.db.command.ExportXmlCommand;
import com.sqlapp.data.db.command.SqlExecuteCommand;
import com.sqlapp.jdbc.JdbcUtils;

public class HsqlExportAndGenerateCreateSqlTest extends AbstractExportAndGenerateCreateSqlTest{
	/**
	 * JDBC URL
	 */
	private String url="jdbc:hsqldb:.";
	/**
	 * JDBC Driver Class Name
	 */
	private String driverClassName=JdbcUtils
			.getDriverClassNameByUrl(url);

	/**
	 * JDBC User Name
	 */
	private String username=null;
	/**
	 * JDBC Password
	 */
	private String password=null;
	
	public HsqlExportAndGenerateCreateSqlTest(){
		this.url=getTestProp("hsql.jdbc.url");
		this.username=getTestProp("hsql.jdbc.username");
		this.password=getTestProp("hsql.jdbc.password");
	}
	
	@Override
	protected void initialize(Connection connection) throws SQLException {
		executeSqlFileSilent(connection, "create_table1.sql");
		executeSqlFileSilent(connection, "create_table2.sql");
		executeSqlFileSilent(connection, "create_function1.sql");
		executeSqlFileSilent(connection, "create_function2.sql");
		executeSqlFileSilent(connection, "create_procedure1.sql");
		executeSqlFileSilent(connection, "create_procedure2.sql");
		SqlExecuteCommand command=this.createSqlExecuteCommand(connection);
		//command.setSqlText(this.getResource(fileName));
	}

	@Override
	protected void initialize(ExportXmlCommand command) throws SQLException {
		command.setIncludeRowDumpTables("INFORMATION_SCHEMA.DOMAINS,INFORMATION_SCHEMA.PARAMETERS,INFORMATION_SCHEMA.ROUTINES".split(","));
	}
	
	/**
	 * @return the driverClassName
	 */
	public String getDriverClassName() {
		return driverClassName;
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
	 * @return the password
	 */
	public String getPassword() {
		return password;
	}
	
	
}
