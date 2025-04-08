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

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import com.sqlapp.jdbc.function.SqlBiConsumer;
import com.sqlapp.jdbc.function.SqlConsumer;
import com.sqlapp.jdbc.function.SqlFunction;

/**
 * データソースからコネクションの取得、開放を行うクラス
 * 
 * @author tatsuo satoh
 * 
 */
public final class DataSourceConnectionUtils {

	private static final DataSourceConnectionUtils DEFAULT_INSTANCE = new DataSourceConnectionUtils();

	private DataSourceConnectionUtils() {
	}

	public static DataSourceConnectionUtils getInstance() {
		return DEFAULT_INSTANCE;
	}

	private static GetConnection getConnection = ds -> ds.getConnection();

	private static ReleaseConnection releaseConnection = (ds, conn) -> conn.close();

	public static Connection get(final DataSource dataSource) throws SQLException {
		return getConnection.apply(dataSource);
	}

	public static void release(final DataSource dataSource, final Connection connection) throws SQLException {
		if (connection == null) {
			return;
		}
		releaseConnection.accept(dataSource, connection);
	}

	public static void setGetConnection(final GetConnection getConnection) {
		DataSourceConnectionUtils.getConnection = getConnection;
	}

	public static void setReleaseConnection(final ReleaseConnection releaseConnection) {
		DataSourceConnectionUtils.releaseConnection = releaseConnection;
	}

	public static void execute(DataSource dataSource, SqlConsumer<Connection> cons) throws SQLException {
		Connection connection = get(dataSource);
		try {
			cons.accept(connection);
		} catch (SQLException e) {
			throw e;
		} finally {
			release(dataSource, connection);
		}
	}

	public static void executeTran(DataSource dataSource, SqlConsumer<Connection> cons) throws SQLException {
		Connection connection = get(dataSource);
		try {
			connection.setAutoCommit(false);
			cons.accept(connection);
			connection.commit();
		} catch (SQLException e) {
			connection.rollback();
			throw e;
		} finally {
			release(dataSource, connection);
		}
	}

	/**
	 * コネクションの取得用のインタフェース
	 * 
	 */
	@FunctionalInterface
	public static interface GetConnection extends SqlFunction<DataSource, Connection> {
		/**
		 * コネクションを取得します
		 * 
		 */
		Connection apply(final DataSource ds) throws SQLException;
	}

	/**
	 * コネクションの取得用のインタフェース
	 * 
	 */
	@FunctionalInterface
	public static interface ReleaseConnection extends SqlBiConsumer<DataSource, Connection> {
		/**
		 * コネクションを取得します
		 * 
		 */
		void accept(final DataSource ds, final Connection con) throws SQLException;
	}
}
