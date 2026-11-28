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

import java.io.Closeable;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ConnectionBuilder;
import java.sql.SQLException;
import java.sql.ShardingKey;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

import javax.sql.DataSource;

import com.sqlapp.util.FileUtils;

/**
 * データソース抽象クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractDataSource extends AbstractJdbc<DataSource> implements DataSource, Closeable {

	public AbstractDataSource(final DataSource nativeObject) {
		super(nativeObject);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.sql.DataSource#getConnection()
	 */
	@Override
	public Connection getConnection() throws SQLException {
		final Connection connection = nativeObject.getConnection();
		return getConnection(connection);
	}

	protected abstract AbstractConnection getConnection(Connection connection);

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.sql.DataSource#getConnection(java.lang.String, java.lang.String)
	 */
	@Override
	public Connection getConnection(final String username, final String password) throws SQLException {
		final Connection connection = nativeObject.getConnection(username, password);
		return getConnection(connection);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.sql.CommonDataSource#getLogWriter()
	 */
	@Override
	public PrintWriter getLogWriter() throws SQLException {
		return nativeObject.getLogWriter();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.sql.CommonDataSource#getLoginTimeout()
	 */
	@Override
	public int getLoginTimeout() throws SQLException {
		return nativeObject.getLoginTimeout();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.sql.CommonDataSource#setLogWriter(java.io.PrintWriter)
	 */
	@Override
	public void setLogWriter(final PrintWriter out) throws SQLException {
		nativeObject.setLogWriter(out);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.sql.CommonDataSource#setLoginTimeout(int)
	 */
	@Override
	public void setLoginTimeout(final int seconds) throws SQLException {
		nativeObject.setLoginTimeout(seconds);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.sql.CommonDataSource#getParentLogger()
	 */
	@Override
	public Logger getParentLogger() throws SQLFeatureNotSupportedException {
		return nativeObject.getParentLogger();
	}

	@Override
	public void close() {
		if (nativeObject instanceof Closeable) {
			FileUtils.close((Closeable) nativeObject);
		} else if (nativeObject instanceof AutoCloseable) {
			FileUtils.close((AutoCloseable) nativeObject);
		}
	}

	/**
	 * Returns a builder whose connections retain this data source's wrapper
	 * settings.
	 */
	@Override
	public ConnectionBuilder createConnectionBuilder() throws SQLException {
		return new ConnectionBuilder() {
			private ConnectionBuilder delegate = nativeObject.createConnectionBuilder();

			@Override
			public ConnectionBuilder user(final String username) {
				delegate = delegate.user(username);
				return this;
			}

			@Override
			public ConnectionBuilder password(final String password) {
				delegate = delegate.password(password);
				return this;
			}

			@Override
			public ConnectionBuilder shardingKey(final ShardingKey shardingKey) {
				delegate = delegate.shardingKey(shardingKey);
				return this;
			}

			@Override
			public ConnectionBuilder superShardingKey(final ShardingKey superShardingKey) {
				delegate = delegate.superShardingKey(superShardingKey);
				return this;
			}

			@Override
			public Connection build() throws SQLException {
				return getConnection(delegate.build());
			}
		};
	}
}
