/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 *
 * sqlapp-core-dialect-test is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-dialect-test is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-dialect-test.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.command.oracle;

import com.sqlapp.data.db.command.AbstractExportAndGenerateCreateSqlTest;
import com.sqlapp.util.CommonUtils;

public class OracleExportAndGenerateCreateSqlTest extends AbstractExportAndGenerateCreateSqlTest{
	/**
	 * JDBC Driver Class Name
	 */
	private String driverClassName="oracle.jdbc.driver.OracleDriver";
	/**
	 * JDBC URL
	 */
	private String url;
	/**
	 * JDBC User Name
	 */
	private String username;
	/**
	 * JDBC Password
	 */
	private String password;
	
	public OracleExportAndGenerateCreateSqlTest(){
		this.setIncludeSchemas(CommonUtils.split(getTestProp("oracle.includeSchemas"),","));
		this.url=getTestProp("oracle.jdbc.url");
		this.username=getTestProp("oracle.jdbc.username");
		this.password=getTestProp("oracle.jdbc.password");
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
