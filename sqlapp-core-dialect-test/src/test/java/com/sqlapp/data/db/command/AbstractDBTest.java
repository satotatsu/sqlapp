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

package com.sqlapp.data.db.command;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import com.sqlapp.jdbc.JdbcUtils;
import com.sqlapp.jdbc.SqlappDataSource;
import com.zaxxer.hikari.HikariConfig;

public abstract class AbstractDBTest extends AbstractTest {

	@BeforeEach
	public void before() {
	}

	@AfterEach
	public void after() {
	}

	protected HikariConfig getPoolConfiguration() {
		final HikariConfig poolConfiguration = new HikariConfig();
		poolConfiguration.setJdbcUrl(this.getUrl());
		poolConfiguration.setDriverClassName(JdbcUtils.getDriverClassNameByUrl(this.getUrl()));
		if (getUsername() != null) {
			poolConfiguration.setUsername(this.getUsername());
		}
		if (getPassword() != null) {
			poolConfiguration.setPassword(this.getPassword());
		}
		return poolConfiguration;
	}

	protected DataSource newDataSource() {
		final DataSource ds = new SqlappDataSource(new com.zaxxer.hikari.HikariDataSource(getPoolConfiguration()));
		return ds;
	}

	/**
	 * @return the url
	 */
	public String getUrl() {
		return getTestProp(this.getClass().getPackageName() + ".jdbc.url");
	}

	/**
	 * @return the username
	 */
	public String getUsername() {
		return getTestProp(this.getClass().getPackageName() + ".jdbc.username");
	}

	/**
	 * @return the password
	 */
	public String getPassword() {
		return getTestProp(this.getClass().getPackageName() + ".jdbc.password");
	}

}
