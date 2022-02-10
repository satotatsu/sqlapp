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

/**
 * データソースからコネクションの取得、開放を行うクラス
 * 
 * @author tatsuo satoh
 * 
 */
public class DataSourceConnectionHandler implements ConnectionHandler {

	private GetConnectionHandler getConnectionHandler;
	
	private ReleaseConnectionHandler releaseConnectionHandler;

	private DataSource dataSource;

	public DataSourceConnectionHandler() {

	}

	public DataSourceConnectionHandler(final DataSource dataSource) {
		setDataSource(dataSource);
	}

	public DataSource getDataSource() {
		return dataSource;
	}

	public void setDataSource(final DataSource dataSource) {
		this.dataSource = dataSource;
		getConnectionHandler=()->DataSourceConnectionUtils.get(dataSource);
		releaseConnectionHandler=(conn)->DataSourceConnectionUtils.release(dataSource, conn);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.jdbc.ConnectionHandler#getConnection()
	 */
	@Override
	public Connection getConnection() throws SQLException {
		return getConnectionHandler.getConnection();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.jdbc.ConnectionHandler#releaseConnection(java.sql.Connection)
	 */
	@Override
	public void releaseConnection(final Connection connection) throws SQLException {
		releaseConnectionHandler.releaseConnection(connection);
	}

}
