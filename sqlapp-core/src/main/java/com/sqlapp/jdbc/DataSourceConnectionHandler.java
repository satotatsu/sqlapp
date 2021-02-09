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
public class DataSourceConnectionHandler implements ConnectionHandler {

	private DataSource dataSource = null;

	private static final DataSourceConnectionHandler DEFAULT_INSTANCE=new DataSourceConnectionHandler();
	
	public static DataSourceConnectionHandler getInstance() {
		return DEFAULT_INSTANCE;
	}
	
	private final GetConnection getConnection = ds->ds.getConnection();
	
	private final ReleaseConnection releaseConnection = (ds, conn)->DbUtils.close(conn);
	
	public DataSourceConnectionHandler() {

	}

	public DataSourceConnectionHandler(final DataSource dataSource) {
		this.dataSource = dataSource;
	}

	/**
	 * @return the dataSource
	 */
	protected DataSource getDataSource() {
		return dataSource;
	}

	/**
	 * @param dataSource
	 *            the dataSource to set
	 */
	protected void setDataSource(final DataSource dataSource) {
		this.dataSource = dataSource;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.jdbc.ConnectionHandler#getConnection()
	 */
	@Override
	public Connection getConnection() throws SQLException {
		return getConnection.getConnection(this.getDataSource());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.jdbc.ConnectionHandler#releaseConnection(java.sql.Connection)
	 */
	@Override
	public void releaseConnection(final Connection connection) throws SQLException {
		releaseConnection.releaseConnection(this.getDataSource(), connection);
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
