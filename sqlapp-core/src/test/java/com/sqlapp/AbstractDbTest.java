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

package com.sqlapp;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.jdbc.DataSourceConnectionUtils;
import com.sqlapp.jdbc.SqlappDataSource;
import com.sqlapp.jdbc.function.SQLConsumer;
import com.zaxxer.hikari.HikariDataSource;

public class AbstractDbTest extends AbstractTest {

	protected DataSource createDataSource() throws SQLException {
		final HikariDataSource dataSource = new HikariDataSource();
		dataSource.setJdbcUrl("jdbc:hsqldb:mem:test");
		dataSource.setMaximumPoolSize(2);
		dataSource.setMinimumIdle(1);
		// dataSource.setDriverClassName("org.hsqldb.jdbcDriver");
		return new SqlappDataSource(dataSource);
	}

	protected void testDb(SQLConsumer<Connection> cons) throws SQLException {
		DataSource dataSource = createDataSource();
		DataSourceConnectionUtils.executeTranAndCloseDataSource(dataSource, conn -> {
			cons.accept(conn);
		});
	}

	protected void testDb(SQLConsumer<Connection> cons, SQLConsumer<Connection> finCons) throws SQLException {
		DataSource dataSource = createDataSource();
		DataSourceConnectionUtils.executeTranAndCloseDataSource(dataSource, conn -> {
			try {
				cons.accept(conn);
			} finally {
				finCons.accept(conn);
			}
		});
	}

	protected void execute(final Connection conn, final String... sqls) throws SQLException {
		for (final String sql : sqls) {
			try (Statement stmt = conn.createStatement()) {
				stmt.execute(sql);
			}
		}
	}

	protected void dropTables(final Connection conn, final String... tables) {
		for (final String table : tables) {
			try (Statement stmt = conn.createStatement()) {
				stmt.execute("drop table \"" + table + "\" IF EXISTS");
			} catch (final SQLException e) {
			}
		}
	}

	protected void rollback(final Connection connection) {
		if (connection == null) {
			return;
		}
		try {
			connection.rollback();
		} catch (final SQLException e) {
		}
	}

	/**
	 * @return the dialect
	 */
	public Dialect getDialect(Connection connection) {
		Dialect dialect = DialectResolver.getInstance().getDialect(connection);
		return dialect;
	}

	protected String getCurrentCatalogName(final Connection connection) throws SQLException {
		return connection.getCatalog();
	}

	protected String getCurrentSchemaName(final Connection connection) throws SQLException {
		return connection.getSchema();
	}

	protected void executeSql(final Connection connection, final String sql) throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			stmt.execute(sql);
		}
	}
}
