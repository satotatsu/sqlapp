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

import com.sqlapp.jdbc.function.ExceptionConsumer;
import com.sqlapp.jdbc.function.SQLFunction;

/**
 * データソースからコネクションの取得、開放を行うクラス
 * <p>Connection release failures are reported as runtime exceptions. Execution helpers
 * retain the operation failure and attach a concurrent release failure as a suppressed exception.</p>
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

	private static SQLFunction<DataSource, Connection> getConnectionHandler = ds -> ds.getConnection();

	private static ReleaseConnectionHandler releaseConnectionAndCloseDataSourceHandler = new ReleaseConnectionAndCloseDataSourceHandler();

	private static ReleaseConnectionHandler releaseConnectionHandler = new ReleaseConnectionOnlyHandler();

	private static ConnectionExceptionHandler exceptionHandler = new RollbackExceptionHandler();

	public static Connection get(final DataSource dataSource) throws SQLException {
		return getConnectionHandler.apply(dataSource);
	}

	public static void releaseConnectionAndCloseDataSource(final DataSource dataSource, final Connection connection) {
		try {
			releaseConnectionAndCloseDataSourceHandler.accept(dataSource, connection);
		} catch (SQLException e) {
			throw new RuntimeException("Failed to release JDBC connection", e);
		}
	}

	public static void releaseConnection(final DataSource dataSource, final Connection connection) {
		if (connection == null) {
			return;
		}
		try {
			releaseConnectionHandler.accept(dataSource, connection);
		} catch (SQLException e) {
			throw new RuntimeException("Failed to release JDBC connection", e);
		}
	}

	public static void setGetConnectionHandler(final SQLFunction<DataSource, Connection> getConnectionHandler) {
		DataSourceConnectionUtils.getConnectionHandler = getConnectionHandler;
	}

	/**
	 * @param releaseConnectionAndCloseDataSourceHandler the
	 *                                                   releaseConnectionAndCloseDataSourceHandler
	 *                                                   to set
	 */
	public static void setReleaseConnectionAndCloseDataSourceHandler(
			ReleaseConnectionHandler releaseConnectionAndCloseDataSourceHandler) {
		DataSourceConnectionUtils.releaseConnectionAndCloseDataSourceHandler = releaseConnectionAndCloseDataSourceHandler;
	}

	/**
	 * @param releaseConnectionHandler the releaseConnectionHandler to set
	 */
	public static void setReleaseConnectionHandler(ReleaseConnectionHandler releaseConnectionHandler) {
		DataSourceConnectionUtils.releaseConnectionHandler = releaseConnectionHandler;
	}

	/**
	 * @param exceptionHandler the exceptionHandler to set
	 */
	public static void setExceptionHandler(ConnectionExceptionHandler exceptionHandler) {
		DataSourceConnectionUtils.exceptionHandler = exceptionHandler;
	}

	/**
	 * データソースからコネクションを取得して処理を行い、コネクションとデータソースのクローズを行います
	 * 
	 * @param dataSource DataSource
	 * @param cons       行う処理
	 */
	public static void executeAndCloseDataSource(DataSource dataSource, ExceptionConsumer<Connection> cons) {
		execute(dataSource, cons, false, true);
	}

	/**
	 * データソースからコネクションを取得して処理を行い、コネクションとデータソースのクローズを行います
	 * 
	 * @param dataSource DataSource
	 * @param cons       行う処理
	 */
	public static void executeTranAndCloseDataSource(DataSource dataSource, ExceptionConsumer<Connection> cons) {
		execute(dataSource, cons, true, true);
	}

	/**
	 * データソースからコネクションを取得して処理を行い、コネクションのクローズを行います
	 * 
	 * @param dataSource DataSource
	 * @param cons       行う処理
	 */
	public static void execute(DataSource dataSource, ExceptionConsumer<Connection> cons) {
		execute(dataSource, cons, false, false);
	}

	/**
	 * データソースからコネクションを取得して処理を行い、コネクションのクローズを行います
	 * 
	 * @param dataSource DataSource
	 * @param cons       行う処理
	 */
	public static void executeTran(DataSource dataSource, ExceptionConsumer<Connection> cons) {
		execute(dataSource, cons, true, false);
	}


	private static void execute(DataSource dataSource, ExceptionConsumer<Connection> cons,
			boolean transactional, boolean closeDataSource) {
		final Connection connection;
		try {
			connection = get(dataSource);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
		Throwable failure = null;
		try {
			try {
				if (transactional) { connection.setAutoCommit(false); }
				cons.accept(connection);
				if (transactional) { connection.commit(); }
			} catch (Exception e) {
				exceptionHandler.accept(e, connection);
			}
		} catch (RuntimeException | Error e) {
			failure = e;
			throw e;
		} finally {
			try {
				if (closeDataSource) {
					releaseConnectionAndCloseDataSource(dataSource, connection);
				} else {
					releaseConnection(dataSource, connection);
				}
			} catch (RuntimeException | Error releaseFailure) {
				if (failure == null) { throw releaseFailure; }
				if (failure != releaseFailure) { failure.addSuppressed(releaseFailure); }
			}
		}
	}
}
