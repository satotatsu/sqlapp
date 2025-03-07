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

import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

import org.apache.logging.log4j.ThreadContext;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;

/**
 * JDBCのConnectionをラップするクラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractConnection extends AbstractJdbc<Connection>
		implements Connection {

	private Dialect dialect = null;

	/**
	 * コンストラクタ
	 * 
	 * @param nativeConnection
	 *            ラッピングするコネクション
	 */
	public AbstractConnection(final Connection nativeConnection) {
		super(nativeConnection);
	}

	/**
	 * DB方言を取得します
	 * 
	 */
	public Dialect getDialect() {
		if (dialect == null) {
			try {
				final DatabaseMetaData databaseMetaData = nativeObject.getMetaData();
				dialect = DialectResolver.getInstance().getDialect(
						databaseMetaData);
			} catch (final SQLException e) {
				error(this.getClass().getSimpleName() + "#getDialect()", e);
			}
		}
		return dialect;
	}

	/**
	 * ログ出力前の処理
	 */
	@Override
	protected void logBefore() {
		if (dialect != null) {
			ThreadContext.put("dialect", dialect.toString());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#clearWarnings()
	 */
	@Override
	public void clearWarnings() throws SQLException {
		nativeObject.clearWarnings();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#close()
	 */
	@Override
	public void close() throws SQLException {
		nativeObject.close();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#commit()
	 */
	@Override
	public void commit() throws SQLException {
		info("commit()");
		nativeObject.commit();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#createArrayOf(java.lang.String,
	 * java.lang.Object[])
	 */
	@Override
	public Array createArrayOf(final String typeName, final Object[] elements)
			throws SQLException {
		return nativeObject.createArrayOf(typeName, elements);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#createBlob()
	 */
	@Override
	public Blob createBlob() throws SQLException {
		return nativeObject.createBlob();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#createClob()
	 */
	@Override
	public Clob createClob() throws SQLException {
		return nativeObject.createClob();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#createNClob()
	 */
	@Override
	public NClob createNClob() throws SQLException {
		return nativeObject.createNClob();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#createSQLXML()
	 */
	@Override
	public SQLXML createSQLXML() throws SQLException {
		return nativeObject.createSQLXML();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#createStatement()
	 */
	@Override
	public Statement createStatement() throws SQLException {
		return new SqlappStatement(nativeObject.createStatement(), this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#createStatement(int, int)
	 */
	@Override
	public Statement createStatement(final int resultSetType, final int resultSetConcurrency)
			throws SQLException {
		return new SqlappStatement(nativeObject.createStatement(resultSetType,
				resultSetConcurrency), this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#createStatement(int, int, int)
	 */
	@Override
	public Statement createStatement(final int resultSetType,
			final int resultSetConcurrency, final int resultSetHoldability)
			throws SQLException {
		return new SqlappStatement(nativeObject.createStatement(resultSetType,
				resultSetConcurrency, resultSetHoldability), this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#createStruct(java.lang.String,
	 * java.lang.Object[])
	 */
	@Override
	public Struct createStruct(final String typeName, final Object[] attributes)
			throws SQLException {
		return nativeObject.createStruct(typeName, attributes);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#getAutoCommit()
	 */
	@Override
	public boolean getAutoCommit() throws SQLException {
		return nativeObject.getAutoCommit();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#getCatalog()
	 */
	@Override
	public String getCatalog() throws SQLException {
		return nativeObject.getCatalog();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#getClientInfo()
	 */
	@Override
	public Properties getClientInfo() throws SQLException {
		return nativeObject.getClientInfo();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#getClientInfo(java.lang.String)
	 */
	@Override
	public String getClientInfo(final String name) throws SQLException {
		return nativeObject.getClientInfo(name);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#getHoldability()
	 */
	@Override
	public int getHoldability() throws SQLException {
		return nativeObject.getHoldability();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#getMetaData()
	 */
	@Override
	public DatabaseMetaData getMetaData() throws SQLException {
		final DatabaseMetaData databaseMetaData = nativeObject.getMetaData();
		if (dialect == null) {
			dialect = DialectResolver.getInstance()
					.getDialect(databaseMetaData);
		}
		if (databaseMetaData instanceof SqlappDatabaseMetaData) {
			return databaseMetaData;
		}
		return new SqlappDatabaseMetaData(databaseMetaData, this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#getTransactionIsolation()
	 */
	@Override
	public int getTransactionIsolation() throws SQLException {
		return nativeObject.getTransactionIsolation();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#getTypeMap()
	 */
	@Override
	public Map<String, Class<?>> getTypeMap() throws SQLException {
		return nativeObject.getTypeMap();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#getWarnings()
	 */
	@Override
	public SQLWarning getWarnings() throws SQLException {
		return nativeObject.getWarnings();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#isClosed()
	 */
	@Override
	public boolean isClosed() throws SQLException {
		return nativeObject.isClosed();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#isReadOnly()
	 */
	@Override
	public boolean isReadOnly() throws SQLException {
		return nativeObject.isReadOnly();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#isValid(int)
	 */
	@Override
	public boolean isValid(final int timeout) throws SQLException {
		return nativeObject.isValid(timeout);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#nativeSQL(java.lang.String)
	 */
	@Override
	public String nativeSQL(final String sql) throws SQLException {
		return nativeObject.nativeSQL(sql);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#releaseSavepoint(java.sql.Savepoint)
	 */
	@Override
	public void releaseSavepoint(final Savepoint savepoint) throws SQLException {
		if (isInfoEnabled()) {
			info("releaseSavepoint(" + savepoint + ")");
		}
		nativeObject.releaseSavepoint(savepoint);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#rollback()
	 */
	@Override
	public void rollback() throws SQLException {
		info("rollback()");
		nativeObject.rollback();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#rollback(java.sql.Savepoint)
	 */
	@Override
	public void rollback(final Savepoint savepoint) throws SQLException {
		if (isInfoEnabled()) {
			info("rollback(" + savepoint + ")");
		}
		nativeObject.rollback(savepoint);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#setAutoCommit(boolean)
	 */
	@Override
	public void setAutoCommit(final boolean autoCommit) throws SQLException {
		if (isTraceEnabled()) {
			trace("setAutoCommit(" + autoCommit + ")");
		}
		nativeObject.setAutoCommit(autoCommit);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#setCatalog(java.lang.String)
	 */
	@Override
	public void setCatalog(final String catalog) throws SQLException {
		nativeObject.setCatalog(catalog);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#setClientInfo(java.util.Properties)
	 */
	@Override
	public void setClientInfo(final Properties properties)
			throws SQLClientInfoException {
		nativeObject.setClientInfo(properties);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#setClientInfo(java.lang.String,
	 * java.lang.String)
	 */
	@Override
	public void setClientInfo(final String name, final String value)
			throws SQLClientInfoException {
		nativeObject.setClientInfo(name, value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#setHoldability(int)
	 */
	@Override
	public void setHoldability(final int holdability) throws SQLException {
		if (isTraceEnabled()) {
			trace("setHoldability(" + holdability + ")");
		}
		nativeObject.setHoldability(holdability);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#setReadOnly(boolean)
	 */
	@Override
	public void setReadOnly(final boolean readOnly) throws SQLException {
		if (isTraceEnabled()) {
			trace("setReadOnly(" + readOnly + ")");
		}
		nativeObject.setReadOnly(readOnly);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#setSavepoint()
	 */
	@Override
	public Savepoint setSavepoint() throws SQLException {
		trace("setSavepoint()");
		return nativeObject.setSavepoint();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#setSavepoint(java.lang.String)
	 */
	@Override
	public Savepoint setSavepoint(final String name) throws SQLException {
		if (isTraceEnabled()) {
			trace("setSavepoint(" + name + ")");
		}
		return nativeObject.setSavepoint(name);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#setTransactionIsolation(int)
	 */
	@Override
	public void setTransactionIsolation(final int level) throws SQLException {
		if (isTraceEnabled()) {
			trace("setTransactionIsolation(" + level + ")");
		}
		nativeObject.setTransactionIsolation(level);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#setTypeMap(java.util.Map)
	 */
	@Override
	public void setTypeMap(final Map<String, Class<?>> map) throws SQLException {
		nativeObject.setTypeMap(map);
	}

	@Override
	public void setSchema(final String schema) throws SQLException {
		nativeObject.setSchema(schema);
	}

	@Override
	public String getSchema() throws SQLException {
		return nativeObject.getSchema();
	}

	@Override
	public void abort(final Executor executor) throws SQLException {
		nativeObject.abort(executor);
	}

	@Override
	public void setNetworkTimeout(final Executor executor, final int milliseconds)
			throws SQLException {
		nativeObject.setNetworkTimeout(executor, milliseconds);
	}

	@Override
	public int getNetworkTimeout() throws SQLException {
		return nativeObject.getNetworkTimeout();
	}

	@Override
	public void beginRequest() throws SQLException {
		nativeObject.beginRequest();
	}

	@Override
	public void endRequest() throws SQLException {
		nativeObject.endRequest();
	}

	@Override
	public boolean setShardingKeyIfValid(final java.sql.ShardingKey shardingKey,
	        final java.sql.ShardingKey superShardingKey, final int timeout)
	        throws SQLException {
		return nativeObject.setShardingKeyIfValid(shardingKey, superShardingKey, timeout);
	}

	@Override
	public boolean setShardingKeyIfValid(final java.sql.ShardingKey shardingKey, final int timeout)
	        throws SQLException {
		return nativeObject.setShardingKeyIfValid(shardingKey, timeout);
	}

	@Override
	public void setShardingKey(final java.sql.ShardingKey shardingKey, final java.sql.ShardingKey superShardingKey)
	        throws SQLException {
		nativeObject.setShardingKey(shardingKey, superShardingKey);
	}

	@Override
	public void setShardingKey(final java.sql.ShardingKey shardingKey)
	        throws SQLException {
		nativeObject.setShardingKey(shardingKey);
	}

}
