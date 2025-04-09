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
		}
	}

	public static void releaseConnection(final DataSource dataSource, final Connection connection) {
		if (connection == null) {
			return;
		}
		try {
			releaseConnectionHandler.accept(dataSource, connection);
		} catch (SQLException e) {
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
		Connection connection;
		try {
			connection = get(dataSource);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
		try {
			cons.accept(connection);
		} catch (Exception e) {
			exceptionHandler.accept(e, connection);
		} finally {
			releaseConnectionAndCloseDataSource(dataSource, connection);
		}
	}

	/**
	 * データソースからコネクションを取得して処理を行い、コネクションとデータソースのクローズを行います
	 * 
	 * @param dataSource DataSource
	 * @param cons       行う処理
	 */
	public static void executeTranAndCloseDataSource(DataSource dataSource, ExceptionConsumer<Connection> cons) {
		Connection connection;
		try {
			connection = get(dataSource);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
		try {
			connection.setAutoCommit(false);
			cons.accept(connection);
			connection.commit();
		} catch (Exception e) {
			exceptionHandler.accept(e, connection);
		} finally {
			releaseConnectionAndCloseDataSource(dataSource, connection);
		}
	}

	/**
	 * データソースからコネクションを取得して処理を行い、コネクションのクローズを行います
	 * 
	 * @param dataSource DataSource
	 * @param cons       行う処理
	 */
	public static void execute(DataSource dataSource, ExceptionConsumer<Connection> cons) {
		Connection connection;
		try {
			connection = get(dataSource);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
		try {
			cons.accept(connection);
		} catch (Exception e) {
			exceptionHandler.accept(e, connection);
		} finally {
			releaseConnection(dataSource, connection);
		}
	}

	/**
	 * データソースからコネクションを取得して処理を行い、コネクションのクローズを行います
	 * 
	 * @param dataSource DataSource
	 * @param cons       行う処理
	 */
	public static void executeTran(DataSource dataSource, ExceptionConsumer<Connection> cons) {
		Connection connection;
		try {
			connection = get(dataSource);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
		try {
			connection.setAutoCommit(false);
			cons.accept(connection);
			connection.commit();
		} catch (Exception e) {
			exceptionHandler.accept(e, connection);
		} finally {
			releaseConnection(dataSource, connection);
		}
	}

}
