/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.command.test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import com.sqlapp.jdbc.DataSourceConnectionUtils;
import com.sqlapp.jdbc.JdbcUtils;
import com.sqlapp.jdbc.SqlappDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Export用のテスト
 * 
 * @author tatsuo satoh
 *
 */
public abstract class AbstractDbCommandTest extends AbstractTest {

	/**
	 * JDBC URL
	 */
	protected String url;

	private String username;
	private String password;

	public AbstractDbCommandTest() {
		url = getTestProp("jdbc.url");
		username = getTestProp("jdbc.username");
		password = getTestProp("jdbc.password");
	}

	protected HikariDataSource newInternalDataSource() {
		HikariConfig config = new HikariConfig();
		config.setJdbcUrl(this.getUrl());
		config.setUsername(this.getUsername());
		config.setPassword(this.getPassword());
		config.setMaximumPoolSize(10);
		config.setDriverClassName(JdbcUtils.getDriverClassNameByUrl(this.getUrl()));
		final HikariDataSource ds = new HikariDataSource(config);
		return ds;
	}

	protected SqlappDataSource newDataSource() {
		final SqlappDataSource ds = new SqlappDataSource(newInternalDataSource());
		return ds;
	}

	protected void dropTables(final DataSource dataSource, final String... tables) throws SQLException {
		try (Connection conn = DataSourceConnectionUtils.get(dataSource)) {
			dropTables(conn, tables);
		}
	}

	protected void dropTables(final Connection conn, final String... tables) throws SQLException {
		try (Statement stmt = conn.createStatement()) {
			for (final String table : tables) {
				executeSql(stmt, "drop table \"" + table + "\" IF EXISTS");
			}
		}
	}

	protected void executeSql(final DataSource dataSource, final String sql) throws SQLException {
		try (Connection conn = DataSourceConnectionUtils.get(dataSource)) {
			executeSql(conn, sql);
		}
	}

	protected void executeSql(final Connection conn, final String sql) throws SQLException {
		try (Statement stmt = conn.createStatement()) {
			executeSql(stmt, sql);
		}
	}

	private void executeSql(final Statement stmt, final String sql) throws SQLException {
		stmt.execute(sql);
		System.out.println(sql);
	}

	/**
	 * @return the driverClassName
	 */
	public String getDriverClassName() {
		return null;
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
