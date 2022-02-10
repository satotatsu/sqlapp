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

package com.sqlapp.data.db.sql;

import static com.sqlapp.util.CommonUtils.isEmpty;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;

import javax.sql.DataSource;

import com.sqlapp.util.DbUtils;

/**
 * データソースのOperationExecutor
 * 
 * @author tatsuo satoh
 * 
 */
public class DataSourceSqlExecutor implements SqlExecutor {

	private DataSource dataSource = null;

	public DataSourceSqlExecutor() {

	}

	public DataSourceSqlExecutor(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.dialect.operation.OperationExecuter#execute(java.util
	 * .Collection)
	 */
	@Override
	public void execute(Collection<SqlOperation> operations) throws SQLException {
		Connection connection = null;
		Statement statement = null;
		try {
			connection = getConnection();
			statement = connection.createStatement();
			for (SqlOperation operation : operations) {
				if (!isEmpty(operation.getSqlText())) {
					statement.execute(operation.getSqlText());
				}
			}
		} finally {
			DbUtils.close(statement);
			DbUtils.close(connection);
		}
	}

	@Override
	public void execute(SqlOperation... operations) throws SQLException {
		Connection connection = null;
		Statement statement = null;
		try {
			connection = getConnection();
			statement = connection.createStatement();
			for (SqlOperation operation : operations) {
				if (!isEmpty(operation.getSqlText())) {
					statement.execute(operation.getSqlText());
				}
			}
		} finally {
			DbUtils.close(statement);
			DbUtils.close(connection);
		}
	}

	protected Connection getConnection() throws SQLException {
		Connection connection = getDataSource().getConnection();
		return connection;
	}

	/**
	 * @return the dataSource
	 */
	public DataSource getDataSource() {
		return dataSource;
	}

	/**
	 * @param dataSource
	 *            the dataSource to set
	 */
	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

}
