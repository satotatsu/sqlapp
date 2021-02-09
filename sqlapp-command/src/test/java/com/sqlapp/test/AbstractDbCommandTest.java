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

import com.sqlapp.jdbc.JdbcUtils;
import com.sqlapp.jdbc.SqlappDataSource;
import com.zaxxer.hikari.HikariConfig;
/**
 * Export用のテスト
 * @author tatsuo satoh
 *
 */
public abstract class AbstractDbCommandTest extends AbstractTest{

	protected HikariConfig getPoolConfiguration() {
		final HikariConfig poolConfiguration = new HikariConfig();
		poolConfiguration.setJdbcUrl(this.getUrl());
		if (this.getDriverClassName()!=null) {
			poolConfiguration.setDriverClassName(this.getDriverClassName());
		} else {
			poolConfiguration.setDriverClassName(JdbcUtils.getDriverClassNameByUrl(this.getUrl()));
		}
		poolConfiguration.setUsername(this.getUsername());
		poolConfiguration.setPassword(this.getPassword());
		return poolConfiguration;
	}

	protected SqlappDataSource newDataSource() {
		final SqlappDataSource ds = new SqlappDataSource(
					new com.zaxxer.hikari.HikariDataSource(
							getPoolConfiguration()));
		return ds;
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
		return null;
	}
	/**
	 * @return the username
	 */
	public String getUsername() {
		return null;
	}

	/**
	 * @return the password
	 */
	public String getPassword() {
		return null;
	}
	
	
}
