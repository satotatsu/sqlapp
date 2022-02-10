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

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.List;

import org.apache.logging.log4j.ThreadContext;

public abstract class AbstractStatement<T extends Statement> extends
		AbstractJdbc<T> implements Statement {

	public AbstractStatement(final T nativeObject, final Connection connection) {
		super(nativeObject);
		this.connection = connection;
	}

	protected final Connection connection;

	private final List<String> batchSqlList = list();

	/**
	 * SQLを成型するメソッド
	 * 
	 * @param sql
	 */
	protected String formatSql(final String sql) {
		return sql.trim();
	}

	/**
	 * SQLのログ出力
	 * 
	 * @param sql
	 */
	protected void logSql(final String sql, final long start, final long end) {
		if (isSqlLogEnabled()) {
			ThreadContext.put("process_time", "" + (end - start));
			com.sqlapp.thread.ThreadContext.setSql(sql);
			final String formated=formatSql(sql);
			info(formated);
		}
	}

	/**
	 * ログ出力前の処理
	 */
	@Override
	protected void logBefore() {
		if (connection instanceof AbstractConnection) {
			ThreadContext.put("dialect", ((AbstractConnection) connection).getDialect()
					.toString());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#addBatch(java.lang.String)
	 */
	@Override
	public void addBatch(final String sql) throws SQLException {
		if (isSqlLogEnabled()) {
			batchSqlList.add(sql);
		}
		nativeObject.addBatch(sql);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#cancel()
	 */
	@Override
	public void cancel() throws SQLException {
		nativeObject.cancel();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#clearBatch()
	 */
	@Override
	public void clearBatch() throws SQLException {
		if (isSqlLogEnabled()) {
			batchSqlList.clear();
		}
		nativeObject.clearBatch();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#clearWarnings()
	 */
	@Override
	public void clearWarnings() throws SQLException {
		nativeObject.cancel();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#close()
	 */
	@Override
	public void close() throws SQLException {
		nativeObject.close();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#execute(java.lang.String)
	 */
	@Override
	public boolean execute(final String sql) throws SQLException {
		final long start = System.currentTimeMillis();
		try {
			return nativeObject.execute(sql);
		} finally {
			final long end = System.currentTimeMillis();
			logSql(sql, start, end);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#execute(java.lang.String, int)
	 */
	@Override
	public boolean execute(final String sql, final int autoGeneratedKeys)
			throws SQLException {
		final long start = System.currentTimeMillis();
		try {
			return nativeObject.execute(sql, autoGeneratedKeys);
		} finally {
			final long end = System.currentTimeMillis();
			logSql(sql, start, end);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#execute(java.lang.String, int[])
	 */
	@Override
	public boolean execute(final String sql, final int[] columnIndexes) throws SQLException {
		final long start = System.currentTimeMillis();
		try {
			return nativeObject.execute(sql, columnIndexes);
		} finally {
			final long end = System.currentTimeMillis();
			logSql(sql, start, end);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#execute(java.lang.String, java.lang.String[])
	 */
	@Override
	public boolean execute(final String sql, final String[] columnNames)
			throws SQLException {
		final long start = System.currentTimeMillis();
		try {
			return nativeObject.execute(sql, columnNames);
		} finally {
			final long end = System.currentTimeMillis();
			logSql(sql, start, end);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#executeBatch()
	 */
	@Override
	public int[] executeBatch() throws SQLException {

		final long start = System.currentTimeMillis();
		try {
			return nativeObject.executeBatch();
		} finally {
			final long end = System.currentTimeMillis();
			if (isSqlLogEnabled()) {
				final int size = batchSqlList.size();
				logSql("=====START BATCH=====", start, end);
				final StringBuilder builder=new StringBuilder(); 
				for (int i = 0; i < size; i++) {
					builder.append(batchSqlList.get(i));
					builder.append(';');
					builder.append('\n');
				}
				logSql(builder.toString(), start, end);
				logSql("=====END BATCH=====", start, end);
				batchSqlList.clear();
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#executeQuery(java.lang.String)
	 */
	@Override
	public ResultSet executeQuery(final String sql) throws SQLException {
		final long start = System.currentTimeMillis();
		try {
			final ResultSet rs = nativeObject.executeQuery(sql);
			if (rs == null) {
				return null;
			}
			return getResultSet(rs, this);
		} finally {
			final long end = System.currentTimeMillis();
			logSql(sql, start, end);
		}
	}

	protected abstract ResultSet getResultSet(ResultSet rs, Statement statement);

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#executeUpdate(java.lang.String)
	 */
	@Override
	public int executeUpdate(final String sql) throws SQLException {
		final long start = System.currentTimeMillis();
		try {
			return nativeObject.executeUpdate(sql);
		} finally {
			final long end = System.currentTimeMillis();
			logSql(sql, start, end);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#executeUpdate(java.lang.String, int)
	 */
	@Override
	public int executeUpdate(final String sql, final int autoGeneratedKeys)
			throws SQLException {
		final long start = System.currentTimeMillis();
		try {
			return nativeObject.executeUpdate(sql, autoGeneratedKeys);
		} finally {
			final long end = System.currentTimeMillis();
			logSql(sql, start, end);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#executeUpdate(java.lang.String, int[])
	 */
	@Override
	public int executeUpdate(final String sql, final int[] columnIndexes)
			throws SQLException {
		final long start = System.currentTimeMillis();
		try {
			return nativeObject.executeUpdate(sql, columnIndexes);
		} finally {
			final long end = System.currentTimeMillis();
			logSql(sql, start, end);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#executeUpdate(java.lang.String,
	 * java.lang.String[])
	 */
	@Override
	public int executeUpdate(final String sql, final String[] columnNames)
			throws SQLException {
		final long start = System.currentTimeMillis();
		try {
			return nativeObject.executeUpdate(sql, columnNames);
		} finally {
			final long end = System.currentTimeMillis();
			logSql(sql, start, end);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#getConnection()
	 */
	@Override
	public Connection getConnection() throws SQLException {
		return nativeObject.getConnection();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#getFetchDirection()
	 */
	@Override
	public int getFetchDirection() throws SQLException {
		return nativeObject.getFetchDirection();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#getFetchSize()
	 */
	@Override
	public int getFetchSize() throws SQLException {
		return nativeObject.getFetchSize();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#getGeneratedKeys()
	 */
	@Override
	public ResultSet getGeneratedKeys() throws SQLException {
		final ResultSet rs = nativeObject.getGeneratedKeys();
		if (rs == null) {
			return null;
		}
		return getResultSet(rs, this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#getMaxFieldSize()
	 */
	@Override
	public int getMaxFieldSize() throws SQLException {
		return nativeObject.getMaxFieldSize();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#getMaxRows()
	 */
	@Override
	public int getMaxRows() throws SQLException {
		return nativeObject.getMaxRows();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#getMoreResults()
	 */
	@Override
	public boolean getMoreResults() throws SQLException {
		return nativeObject.getMoreResults();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#getMoreResults(int)
	 */
	@Override
	public boolean getMoreResults(final int current) throws SQLException {
		return nativeObject.getMoreResults(current);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#getQueryTimeout()
	 */
	@Override
	public int getQueryTimeout() throws SQLException {
		return nativeObject.getQueryTimeout();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#getResultSet()
	 */
	@Override
	public ResultSet getResultSet() throws SQLException {
		final ResultSet rs = nativeObject.getResultSet();
		if (rs == null) {
			return null;
		}
		return getResultSet(rs, this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#getResultSetConcurrency()
	 */
	@Override
	public int getResultSetConcurrency() throws SQLException {
		return nativeObject.getResultSetConcurrency();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#getResultSetHoldability()
	 */
	@Override
	public int getResultSetHoldability() throws SQLException {
		return nativeObject.getResultSetHoldability();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#getResultSetType()
	 */
	@Override
	public int getResultSetType() throws SQLException {
		return nativeObject.getResultSetType();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#getUpdateCount()
	 */
	@Override
	public int getUpdateCount() throws SQLException {
		return nativeObject.getUpdateCount();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#getWarnings()
	 */
	@Override
	public SQLWarning getWarnings() throws SQLException {
		return nativeObject.getWarnings();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#isClosed()
	 */
	@Override
	public boolean isClosed() throws SQLException {
		return nativeObject.isClosed();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#isPoolable()
	 */
	@Override
	public boolean isPoolable() throws SQLException {
		return nativeObject.isPoolable();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#setCursorName(java.lang.String)
	 */
	@Override
	public void setCursorName(final String name) throws SQLException {
		nativeObject.setCursorName(name);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#setEscapeProcessing(boolean)
	 */
	@Override
	public void setEscapeProcessing(final boolean enable) throws SQLException {
		nativeObject.setEscapeProcessing(enable);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#setFetchDirection(int)
	 */
	@Override
	public void setFetchDirection(final int direction) throws SQLException {
		nativeObject.setFetchDirection(direction);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#setFetchSize(int)
	 */
	@Override
	public void setFetchSize(final int rows) throws SQLException {
		nativeObject.setFetchSize(rows);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#setMaxFieldSize(int)
	 */
	@Override
	public void setMaxFieldSize(final int max) throws SQLException {
		nativeObject.setMaxFieldSize(max);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#setMaxRows(int)
	 */
	@Override
	public void setMaxRows(final int max) throws SQLException {
		nativeObject.setMaxRows(max);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#setPoolable(boolean)
	 */
	@Override
	public void setPoolable(final boolean poolable) throws SQLException {
		nativeObject.setPoolable(poolable);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#setQueryTimeout(int)
	 */
	@Override
	public void setQueryTimeout(final int seconds) throws SQLException {
		nativeObject.setQueryTimeout(seconds);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#closeOnCompletion()
	 */
	@Override
	public void closeOnCompletion() throws SQLException {
		nativeObject.closeOnCompletion();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#isCloseOnCompletion()
	 */
	@Override
	public boolean isCloseOnCompletion() throws SQLException {
		return nativeObject.isCloseOnCompletion();
	}
	

	@Override
	public long getLargeUpdateCount() throws SQLException {
		return nativeObject.getLargeUpdateCount();
	}

	@Override
	public void setLargeMaxRows(final long max) throws SQLException {
		nativeObject.setLargeMaxRows(max);
	}

	@Override
	public long getLargeMaxRows() throws SQLException {
		return nativeObject.getLargeMaxRows();
	}

	@Override
	public long[] executeLargeBatch() throws SQLException {
		return nativeObject.executeLargeBatch();
	}

	@Override
	public long executeLargeUpdate(final String sql) throws SQLException {
		return nativeObject.executeLargeUpdate(sql);
	}

	@Override
	public long executeLargeUpdate(final String sql, final int autoGeneratedKeys)
	        throws SQLException {
		return nativeObject.executeLargeUpdate(sql, autoGeneratedKeys);
	}

	@Override
	public long executeLargeUpdate(final String sql, final int columnIndexes[]) throws SQLException {
		return nativeObject.executeLargeUpdate(sql, columnIndexes);
	}

	@Override
	public long executeLargeUpdate(final String sql, final String columnNames[])
	        throws SQLException {
		return nativeObject.executeLargeUpdate(sql, columnNames);
	}

	@Override
	public String enquoteLiteral(final String val)  throws SQLException {
		return nativeObject.enquoteLiteral(val);
	}

	@Override
	public String enquoteIdentifier(final String identifier, final boolean alwaysQuote) throws SQLException {
		return nativeObject.enquoteIdentifier(identifier, alwaysQuote);
	}

	@Override
	public boolean isSimpleIdentifier(final String identifier) throws SQLException {
		return nativeObject.isSimpleIdentifier(identifier);
	}

	@Override
	public String enquoteNCharLiteral(final String val)  throws SQLException {
		return nativeObject.enquoteNCharLiteral(val);
	}

}
