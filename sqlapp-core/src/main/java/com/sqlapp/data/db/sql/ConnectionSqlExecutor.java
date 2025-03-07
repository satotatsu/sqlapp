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

package com.sqlapp.data.db.sql;

import static com.sqlapp.util.CommonUtils.isEmpty;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;

import com.sqlapp.util.DbUtils;

/**
 * ConnectionのOperationExecuter
 * 
 * @author tatsuo satoh
 * 
 */
public class ConnectionSqlExecutor implements SqlExecutor {

	private Connection connection = null;

	public ConnectionSqlExecutor(Connection connection) {
		this.connection = connection;
	}
	
	private boolean autoClose=true;

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.dialect.operation.OperationExecuter#execute(java.util
	 * .Collection)
	 */
	@Override
	public void execute(Collection<SqlOperation> operations) throws SQLException {
		Connection connection = this.getConnection();
		Statement statement = null;
		try {
			statement = connection.createStatement();
			for (SqlOperation operation : operations) {
				if (!isEmpty(operation.getSqlText())) {
					statement.execute(operation.getSqlText());
				}
			}
		} finally {
			DbUtils.close(statement);
			close(connection);
		}
	}

	@Override
	public void execute(SqlOperation... operations) throws SQLException {
		Connection connection = this.getConnection();
		Statement statement = null;
		try {
			statement = connection.createStatement();
			for (SqlOperation operation : operations) {
				if (!isEmpty(operation.getSqlText())) {
					statement.execute(operation.getSqlText());
				}
			}
		} finally {
			DbUtils.close(statement);
			close(connection);
		}
	}

	public Connection getConnection() {
		return connection;
	}

	protected void close(Connection connection) {
		try {
			if (isAutoClose()){
				connection.close();
			}
		} catch (SQLException e) {
		}
	}

	/**
	 * @param connection the connection to set
	 */
	public void setConnection(Connection connection) {
		this.connection = connection;
	}

	/**
	 * @return the autoClose
	 */
	public boolean isAutoClose() {
		return autoClose;
	}

	/**
	 * @param autoClose the autoClose to set
	 */
	public void setAutoClose(boolean autoClose) {
		this.autoClose = autoClose;
	}

}
