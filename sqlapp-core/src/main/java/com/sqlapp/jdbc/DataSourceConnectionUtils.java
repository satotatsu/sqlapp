/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import com.sqlapp.util.DbUtils;

/**
 * データソースからコネクションの取得、開放を行うクラス
 * 
 * @author tatsuo satoh
 * 
 */
public final class DataSourceConnectionUtils {

	private static final DataSourceConnectionUtils DEFAULT_INSTANCE=new DataSourceConnectionUtils();
	
	public static DataSourceConnectionUtils getInstance() {
		return DEFAULT_INSTANCE;
	}
	
	private static GetConnection getConnection = ds->ds.getConnection();
	
	private static ReleaseConnection releaseConnection = (ds, conn)->DbUtils.close(conn);

	public static Connection get(final DataSource dataSource) throws SQLException {
		return getConnection.getConnection(dataSource);
	}

	public static void release(final DataSource dataSource, final Connection connection) throws SQLException {
		releaseConnection.releaseConnection(dataSource, connection);
	}

	public static void setGetConnection(final GetConnection getConnection) {
		DataSourceConnectionUtils.getConnection = getConnection;
	}

	public static void setReleaseConnection(final ReleaseConnection releaseConnection) {
		DataSourceConnectionUtils.releaseConnection = releaseConnection;
	}

	/**
	 * コネクションの取得用のインタフェース
	 * 
	 */
	@FunctionalInterface
	static interface GetConnection {
		/**
		 * コネクションを取得します
		 * 
		 */
		Connection getConnection(final DataSource ds) throws SQLException;
	}
	
	/**
	 * コネクションの取得用のインタフェース
	 * 
	 */
	@FunctionalInterface
	static interface ReleaseConnection {
		/**
		 * コネクションを取得します
		 * 
		 */
		void releaseConnection(final DataSource ds, final Connection con) throws SQLException;
	}
}
