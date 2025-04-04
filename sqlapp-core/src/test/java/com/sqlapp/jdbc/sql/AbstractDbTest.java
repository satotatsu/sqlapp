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

package com.sqlapp.jdbc.sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import com.sqlapp.AbstractTest;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.jdbc.SqlappDataSource;
import com.zaxxer.hikari.HikariDataSource;

public class AbstractDbTest extends AbstractTest {

	protected DataSource dataSource;

	protected DataSource createDataSource() throws SQLException {
		final HikariDataSource dataSource = new HikariDataSource();
		dataSource.setJdbcUrl("jdbc:hsqldb:mem:test");
		// dataSource.setDriverClassName("org.hsqldb.jdbcDriver");
		return new SqlappDataSource(dataSource);
	}

	protected void testDb(SqlConsumer<Connection> cons) throws SQLException {
		DataSource dataSource = createDataSource();
		try (Connection conn = dataSource.getConnection()) {
			cons.accept(conn);
		}
	}

	protected void testDb(SqlConsumer<Connection> cons, SqlConsumer<Connection> finCons) throws SQLException {
		DataSource dataSource = createDataSource();
		try (Connection conn = dataSource.getConnection()) {
			try {
				cons.accept(conn);
			} catch (SQLException e) {
				try {
					finCons.accept(conn);
				} catch (SQLException e1) {
				}
				throw e;
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

	@FunctionalInterface
	interface SqlConsumer<T> {
		void accept(T obj) throws SQLException;
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
	 * @return the dataSource
	 */
	public DataSource getDataSource() {
		return dataSource;
	}

	/**
	 * @param dataSource the dataSource to set
	 */
	public void setDataSource(final DataSource dataSource) {
		this.dataSource = dataSource;
	}

	/**
	 * @return the dialect
	 */
	public Dialect getDialect(Connection connection) {
		Dialect dialect = DialectResolver.getInstance().getDialect(connection);
		return dialect;
	}

	protected String getCurrentCatalogName(final Connection connection, final Dialect dialect) {
		return dialect.getCatalogReader().getCurrentCatalogName(connection);
	}

	protected String getCurrentSchemaName(final Connection connection, final Dialect dialect) {
		return dialect.getCatalogReader().getSchemaReader().getCurrentSchemaName(connection);
	}

	protected void executeSql(final Connection connection, final String sql) throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			stmt.execute(sql);
		}
	}
}
