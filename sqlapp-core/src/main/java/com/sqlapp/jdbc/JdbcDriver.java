/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.jdbc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.exceptions.JdbcDriverNotFoundException;

public enum JdbcDriver {
	DB2("jdbc:db2:(.*)", "com.ibm.db2.jcc.DB2Driver"),
	DB2iseries("jdbc:as400:(.*)", "com.ibm.as400.access.AS400JDBCDriver"),
	Derby("jdbc:derby:(//.*)","org.apache.derby.jdbc.ClientDriver40"),
	DerbyEmbedded("jdbc:derby:(.*)","org.apache.derby.jdbc.EmbeddedDriver"),
	Firebird("jdbc:firebirdsql:(.*)", "org.firebirdsql.jdbc.FBDriver"),
	H2("jdbc:h2:(.*)", "org.h2.Driver"),
	HiRDB("jdbc:hitachi:hirdb:(.*)","JP.co.Hitachi.soft.HiRDB.JDBC.HiRDBDriver"),
	HSQLDB("jdbc:hsqldb:(.*)", "org.hsqldb.jdbcDriver"),
	jTDS("jdbc:jtds:(.*)","net.sourceforge.jtds.jdbc.Driver"),
	MariaDB("jdbc:mariadb:(.*)", "org.mariadb.jdbc.Driver"),
	MySQL("jdbc:mysql:(.*)", "com.mysql.cj.jdbc.Driver", "com.mysql.jdbc.Driver"),
	MSSqlserver("jdbc:sqlserver:(.*)","com.microsoft.sqlserver.jdbc.SQLServerDriver"),
	MSSqlserverWeblogic("jdbc:weblogic:mssqlserver4:(.*)","weblogic.jdbc.mssqlserver4.Driver"),
	OJDBC("jdbc:odbc:(.*)", "sun.jdbc.odbc.JdbcOdbcDriver"),
	Oracle("jdbc:oracle:(.*)", "oracle.jdbc.driver.OracleDriver"),
	Phoenix("jdbc:phoenix:(.*)", "org.apache.phoenix.queryserver.client.Driver"),
	Postgres("jdbc:postgresql:(.*)", "org.postgresql.Driver", "postgresql.Driver"),
	SAPHANA("jdbc:sap:(.*)", "com.sap.db.jdbc.Driver"),
	SQLite("jdbc:sqlite:(.*)", "org.sqlite.JDBC"),
	Sybase("jdbc:sybase:(.*)", "com.sybase.jdbc3.jdbc.SybDriver", "com.sybase.jdbc2.jdbc.SybDriver", "com.sybase.jdbc.SybDriver"),
	Teradata("jdbc:teradata:(.*)", "com.teradata.jdbc.TeraDriver"),
	Virtica("jdbc:vertica:(.*)","com.vertica.jdbc.Driver"),
	;
	
	private JdbcDriver(String url, String... driverClassName){
		this.urlPattern=Pattern.compile(url);
		this.driverClassName=driverClassName;
	}
	
	
	private final Pattern urlPattern;

	private final String[] driverClassName;

	private boolean match(String url){
		Matcher matcher=urlPattern.matcher(url);
		return matcher.matches();
	}
	
	
	public String DriverClassName(){
		for(String name:driverClassName){
			try {
				Class.forName(name);
				return name;
			} catch (ClassNotFoundException e) {
			}
		}
		return null;
	}
	
	public String DriverClassName(ClassLoader classLoader){
		for(String name:driverClassName){
			try {
				Class.forName(name, true, classLoader);
				return name;
			} catch (ClassNotFoundException e) {
			}
		}
		return null;
	}

	/**
	 * URLからJDBCドライバークラスを取得します
	 * 
	 * @param url
	 */
	public static String getDriverClassNameByUrl(String url) {
		if (url==null){
			return null;
		}
		for (JdbcDriver enm : values()) {
			if (enm.match(url)) {
				String driverClassName=enm.DriverClassName();
				if (driverClassName==null){
					throw new JdbcDriverNotFoundException("Driver not found. url="+url);
				}
				return driverClassName;
			}
		}
		return null;
	}
	
	/**
	 * URLからJDBCドライバークラスを取得します
	 * 
	 * @param url
	 */
	public static String getDriverClassNameByUrl(String url, ClassLoader classLoader) {
		if (url==null){
			return null;
		}
		for (JdbcDriver enm : values()) {
			if (enm.match(url)) {
				String driverClassName=enm.DriverClassName(classLoader);
				if (driverClassName==null){
					throw new RuntimeException("Driver not found. url="+url);
				}
				return driverClassName;
			}
		}
		return null;
	}
}
