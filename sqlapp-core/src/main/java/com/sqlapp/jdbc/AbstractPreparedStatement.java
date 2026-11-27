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

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.List;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.util.DateUtils;
import com.sqlapp.util.FlexList;

public abstract class AbstractPreparedStatement<T extends PreparedStatement> extends AbstractStatement<T>
		implements PreparedStatement {

	private final List<Object> parameters = new FlexList<Object>();

	private String[] sqlParts = null;

	private String sql = null;

	public AbstractPreparedStatement(final T nativeStatement, final String sql, final SqlappConnection connection) {
		super(nativeStatement, connection);
		this.sql = sql;
		final String[] parts = sql.split("[?]", -1);
		this.sqlParts = parts;
	}

	/**
	 * ログ出力するSQLを作成します
	 * 
	 */
	private String getLogSql() {
		if (isSqlLogEnabled()) {
			// Do not guess marker positions in vendor-specific SQL syntax. Preserve
			// the original SQL and report bindings separately when interpolation is ambiguous.
			if (sql.indexOf('\'') >= 0 || sql.indexOf('"') >= 0 || sql.indexOf('`') >= 0
					|| sql.indexOf('[') >= 0 || sql.indexOf('$') >= 0 || sql.indexOf('#') >= 0
					|| sql.contains("--") || sql.contains("/*") || sql.contains("??")
					|| parameters.size() != sqlParts.length - 1) {
				return sql + "\n/* parameters: " + parameters + " */";
			}
			final StringBuilder builder = new StringBuilder(sql.length() * 2);
			final int size = parameters.size();
			builder.append(sqlParts[0]);
			for (int i = 0; i < size; i++) {
				builder.append(parameters.get(i));
				if ((i + 1) < sqlParts.length) {
					builder.append(sqlParts[i + 1]);
				}
			}
			return builder.toString();
		}
		return "";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#addBatch()
	 */
	@Override
	public void addBatch() throws SQLException {
		this.nativeObject.addBatch();
		if (isSqlLogEnabled()) {
			recordBatchSql(getLogSql());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#clearParameters()
	 */
	@Override
	public void clearParameters() throws SQLException {
		this.nativeObject.clearParameters();
		parameters.clear();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#execute()
	 */
	@Override
	public boolean execute() throws SQLException {
		final long start = System.currentTimeMillis();
		try {
			return nativeObject.execute();
		} finally {
			final long end = System.currentTimeMillis();
			logSql(getLogSql(), start, end);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#executeQuery()
	 */
	@Override
	public ResultSet executeQuery() throws SQLException {
		final long start = System.currentTimeMillis();
		try {
			final ResultSet rs = nativeObject.executeQuery();
			if (rs == null) {
				return null;
			}
			return getResultSet(rs, this);
		} finally {
			final long end = System.currentTimeMillis();
			logSql(getLogSql(), start, end);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#executeUpdate()
	 */
	@Override
	public int executeUpdate() throws SQLException {
		final long start = System.currentTimeMillis();
		try {
			return nativeObject.executeUpdate();
		} finally {
			final long end = System.currentTimeMillis();
			logSql(this.getLogSql(), start, end);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#getMetaData()
	 */
	@Override
	public ResultSetMetaData getMetaData() throws SQLException {
		return nativeObject.getMetaData();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#getParameterMetaData()
	 */
	@Override
	public ParameterMetaData getParameterMetaData() throws SQLException {
		return nativeObject.getParameterMetaData();
	}

	@Override
	public void setArray(final int parameterIndex, final Array x) throws SQLException {
		this.nativeObject.setArray(parameterIndex, x);
		if (isSqlLogEnabled()) {
			parameters.set(parameterIndex - 1, x == null ? "null" : x.toString());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setAsciiStream(int, java.io.InputStream)
	 */
	@Override
	public void setAsciiStream(final int parameterIndex, final InputStream x) throws SQLException {
		this.nativeObject.setAsciiStream(parameterIndex, x);
		if (isSqlLogEnabled()) {
			setStringParameter(parameterIndex - 1, x == null ? null : x.toString());
		}
	}

	/**
	 * 文字型のパラメタの設定
	 * 
	 * @param parameterIndex
	 * @param value
	 */
	private void setStringParameter(final int parameterIndex, final String value) {
		if (value == null) {
			parameters.set(parameterIndex, "null");
		} else {
			parameters.set(parameterIndex, "'" + value + "'");
		}
	}

	/**
	 * ユニコード文字型のパラメタの設定
	 * 
	 * @param parameterIndex
	 * @param value
	 */
	private void setNStringParameter(final int parameterIndex, final String value) {
		if (value == null) {
			parameters.set(parameterIndex, "null");
		} else {
			parameters.set(parameterIndex, "N'" + value + "'");
		}
	}

	/**
	 * バイナリのパラメタの設定
	 * 
	 * @param parameterIndex
	 * @param value
	 * @param length
	 */
	private void setBinaryParameter(final int parameterIndex, final String value) {
		if (value == null) {
			parameters.set(parameterIndex, "null");
		} else {
			parameters.set(parameterIndex, "'" + value + "'");
		}
	}

	/**
	 * オブジェクトのパラメタの設定
	 * 
	 * @param parameterIndex
	 * @param value
	 */
	private void setObjectParameter(final int parameterIndex, final Object value) {
		if (value == null) {
			parameters.set(parameterIndex, "null");
		} else if (value instanceof Number) {
			parameters.set(parameterIndex, value);
		} else {
			setStringParameter(parameterIndex, value.toString());
		}
	}

	/**
	 * オブジェクトのパラメタの設定
	 * 
	 * @param parameterIndex
	 * @param value
	 * @param targetSqlType
	 */
	private void setObjectParameter(final int parameterIndex, final Object value, final int targetSqlType) {
		if (value == null) {
			parameters.set(parameterIndex, "null");
			return;
		}
		final DataType type = DataType.valueOf(targetSqlType);
		if (type.isNumeric()) {
			parameters.set(parameterIndex, value);
		} else if (type.isNationalCharacter()) {
			setNStringParameter(parameterIndex, value.toString());
		} else {
			setStringParameter(parameterIndex, value.toString());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setAsciiStream(int, java.io.InputStream, int)
	 */
	@Override
	public void setAsciiStream(final int parameterIndex, final InputStream x, final int length) throws SQLException {
		this.nativeObject.setAsciiStream(parameterIndex, x, length);
		if (isSqlLogEnabled()) {
			if (x == null) {
				setStringParameter(parameterIndex - 1, null);
			} else {
				setStringParameter(parameterIndex - 1, x.toString());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setAsciiStream(int, java.io.InputStream,
	 * long)
	 */
	@Override
	public void setAsciiStream(final int parameterIndex, final InputStream x, final long length) throws SQLException {
		this.nativeObject.setAsciiStream(parameterIndex, x, length);
		if (isSqlLogEnabled()) {
			if (x == null) {
				setStringParameter(parameterIndex - 1, null);
			} else {
				setStringParameter(parameterIndex - 1, x.toString());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setBigDecimal(int, java.math.BigDecimal)
	 */
	@Override
	public void setBigDecimal(final int parameterIndex, final BigDecimal x) throws SQLException {
		this.nativeObject.setBigDecimal(parameterIndex, x);
		if (isSqlLogEnabled()) {
			if (x == null) {
				parameters.set(parameterIndex - 1, null);
			} else {
				parameters.set(parameterIndex - 1, x.toPlainString());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setBinaryStream(int, java.io.InputStream)
	 */
	@Override
	public void setBinaryStream(final int parameterIndex, final InputStream x) throws SQLException {
		this.nativeObject.setBinaryStream(parameterIndex, x);
		if (isSqlLogEnabled()) {
			if (x == null) {
				setBinaryParameter(parameterIndex - 1, null);
			} else {
				setBinaryParameter(parameterIndex - 1, x.toString());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setBinaryStream(int, java.io.InputStream,
	 * int)
	 */
	@Override
	public void setBinaryStream(final int parameterIndex, final InputStream x, final int length) throws SQLException {
		this.nativeObject.setBinaryStream(parameterIndex, x, length);
		if (isSqlLogEnabled()) {
			if (x == null) {
				setBinaryParameter(parameterIndex - 1, null);
			} else {
				setBinaryParameter(parameterIndex - 1, x.toString());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setBinaryStream(int, java.io.InputStream,
	 * long)
	 */
	@Override
	public void setBinaryStream(final int parameterIndex, final InputStream x, final long length) throws SQLException {
		this.nativeObject.setBinaryStream(parameterIndex, x, length);
		if (isSqlLogEnabled()) {
			if (x == null) {
				setBinaryParameter(parameterIndex - 1, null);
			} else {
				setBinaryParameter(parameterIndex - 1, x.toString());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setBlob(int, java.sql.Blob)
	 */
	@Override
	public void setBlob(final int parameterIndex, final Blob x) throws SQLException {
		this.nativeObject.setBlob(parameterIndex, x);
		if (isSqlLogEnabled()) {
			if (x == null) {
				setBinaryParameter(parameterIndex - 1, null);
			} else {
				setBinaryParameter(parameterIndex - 1, x.toString());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setBlob(int, java.io.InputStream)
	 */
	@Override
	public void setBlob(final int parameterIndex, final InputStream inputStream) throws SQLException {
		this.nativeObject.setBlob(parameterIndex, inputStream);
		if (isSqlLogEnabled()) {
			if (inputStream == null) {
				setBinaryParameter(parameterIndex - 1, null);
			} else {
				setBinaryParameter(parameterIndex - 1, inputStream.toString());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setBlob(int, java.io.InputStream, long)
	 */
	@Override
	public void setBlob(final int parameterIndex, final InputStream inputStream, final long length)
			throws SQLException {
		this.nativeObject.setBlob(parameterIndex, inputStream, length);
		if (isSqlLogEnabled()) {
			if (inputStream == null) {
				setBinaryParameter(parameterIndex - 1, null);
			} else {
				setBinaryParameter(parameterIndex - 1, inputStream.toString());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setBoolean(int, boolean)
	 */
	@Override
	public void setBoolean(final int parameterIndex, final boolean x) throws SQLException {
		this.nativeObject.setBoolean(parameterIndex, x);
		if (isSqlLogEnabled()) {
			parameters.set(parameterIndex - 1, x);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setByte(int, byte)
	 */
	@Override
	public void setByte(final int parameterIndex, final byte x) throws SQLException {
		this.nativeObject.setByte(parameterIndex, x);
		if (isSqlLogEnabled()) {
			parameters.set(parameterIndex - 1, x);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setBytes(int, byte[])
	 */
	@Override
	public void setBytes(final int parameterIndex, final byte[] x) throws SQLException {
		this.nativeObject.setBytes(parameterIndex, x);
		if (isSqlLogEnabled()) {
			if (x == null) {
				setBinaryParameter(parameterIndex - 1, null);
			} else {
				// setBinaryParameter(parameterIndex-1, Arrays.toString(x));
				setBinaryParameter(parameterIndex - 1, "byte[]");
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setCharacterStream(int, java.io.Reader)
	 */
	@Override
	public void setCharacterStream(final int parameterIndex, final Reader reader) throws SQLException {
		this.nativeObject.setCharacterStream(parameterIndex, reader);
		if (isSqlLogEnabled()) {
			if (reader == null) {
				setStringParameter(parameterIndex - 1, null);
			} else {
				setStringParameter(parameterIndex - 1, reader.toString());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setCharacterStream(int, java.io.Reader, int)
	 */
	@Override
	public void setCharacterStream(final int parameterIndex, final Reader reader, final int length)
			throws SQLException {
		this.nativeObject.setCharacterStream(parameterIndex, reader, length);
		if (isSqlLogEnabled()) {
			if (reader == null) {
				setStringParameter(parameterIndex - 1, null);
			} else {
				setStringParameter(parameterIndex - 1, reader.toString());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setCharacterStream(int, java.io.Reader, long)
	 */
	@Override
	public void setCharacterStream(final int parameterIndex, final Reader reader, final long length)
			throws SQLException {
		this.nativeObject.setCharacterStream(parameterIndex, reader, length);
		if (isSqlLogEnabled()) {
			if (reader == null) {
				setStringParameter(parameterIndex - 1, null);
			} else {
				setStringParameter(parameterIndex - 1, reader.toString());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setClob(int, java.sql.Clob)
	 */
	@Override
	public void setClob(final int parameterIndex, final Clob x) throws SQLException {
		this.nativeObject.setClob(parameterIndex, x);
		if (isSqlLogEnabled()) {
			if (x == null) {
				setStringParameter(parameterIndex - 1, null);
			} else {
				setStringParameter(parameterIndex - 1, x.toString());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setClob(int, java.io.Reader)
	 */
	@Override
	public void setClob(final int parameterIndex, final Reader reader) throws SQLException {
		this.nativeObject.setClob(parameterIndex, reader);
		if (isSqlLogEnabled()) {
			if (reader == null) {
				setStringParameter(parameterIndex - 1, null);
			} else {
				setStringParameter(parameterIndex - 1, reader.toString());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setClob(int, java.io.Reader, long)
	 */
	@Override
	public void setClob(final int parameterIndex, final Reader reader, final long length) throws SQLException {
		this.nativeObject.setClob(parameterIndex, reader, length);
		if (isSqlLogEnabled()) {
			if (reader == null) {
				setStringParameter(parameterIndex - 1, null);
			} else {
				setStringParameter(parameterIndex - 1, reader.toString());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setDate(int, java.sql.Date)
	 */
	@Override
	public void setDate(final int parameterIndex, final Date x) throws SQLException {
		this.nativeObject.setDate(parameterIndex, x);
		if (isSqlLogEnabled()) {
			if (x == null) {
				setStringParameter(parameterIndex - 1, null);
			} else {
				setStringParameter(parameterIndex - 1, DateUtils.format(x));
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setDate(int, java.sql.Date,
	 * java.util.Calendar)
	 */
	@Override
	public void setDate(final int parameterIndex, final Date x, final Calendar cal) throws SQLException {
		this.nativeObject.setDate(parameterIndex, x, cal);
		if (isSqlLogEnabled()) {
			if (x == null) {
				setStringParameter(parameterIndex - 1, null);
			} else {
				setStringParameter(parameterIndex - 1, DateUtils.format(x));
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setDouble(int, double)
	 */
	@Override
	public void setDouble(final int parameterIndex, final double x) throws SQLException {
		this.nativeObject.setDouble(parameterIndex, x);
		if (isSqlLogEnabled()) {
			parameters.set(parameterIndex - 1, x);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setFloat(int, float)
	 */
	@Override
	public void setFloat(final int parameterIndex, final float x) throws SQLException {
		this.nativeObject.setFloat(parameterIndex, x);
		if (isSqlLogEnabled()) {
			parameters.set(parameterIndex - 1, x);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setInt(int, int)
	 */
	@Override
	public void setInt(final int parameterIndex, final int x) throws SQLException {
		this.nativeObject.setInt(parameterIndex, x);
		if (isSqlLogEnabled()) {
			parameters.set(parameterIndex - 1, x);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setLong(int, long)
	 */
	@Override
	public void setLong(final int parameterIndex, final long x) throws SQLException {
		this.nativeObject.setLong(parameterIndex, x);
		if (isSqlLogEnabled()) {
			parameters.set(parameterIndex - 1, x);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setNCharacterStream(int, java.io.Reader)
	 */
	@Override
	public void setNCharacterStream(final int parameterIndex, final Reader value) throws SQLException {
		this.nativeObject.setNCharacterStream(parameterIndex, value);
		if (isSqlLogEnabled()) {
			if (value == null) {
				setNStringParameter(parameterIndex - 1, null);
			} else {
				setNStringParameter(parameterIndex - 1, value.toString());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setNCharacterStream(int, java.io.Reader,
	 * long)
	 */
	@Override
	public void setNCharacterStream(final int parameterIndex, final Reader value, final long length)
			throws SQLException {
		this.nativeObject.setNCharacterStream(parameterIndex, value, length);
		if (isSqlLogEnabled()) {
			if (value == null) {
				setNStringParameter(parameterIndex - 1, null);
			} else {
				setNStringParameter(parameterIndex - 1, value.toString());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setNClob(int, java.sql.NClob)
	 */
	@Override
	public void setNClob(final int parameterIndex, final NClob value) throws SQLException {
		this.nativeObject.setNClob(parameterIndex, value);
		if (isSqlLogEnabled()) {
			if (value == null) {
				setNStringParameter(parameterIndex - 1, null);
			} else {
				setNStringParameter(parameterIndex - 1, value.toString());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setNClob(int, java.io.Reader)
	 */
	@Override
	public void setNClob(final int parameterIndex, final Reader reader) throws SQLException {
		this.nativeObject.setNClob(parameterIndex, reader);
		if (isSqlLogEnabled()) {
			if (reader == null) {
				setNStringParameter(parameterIndex - 1, null);
			} else {
				setNStringParameter(parameterIndex - 1, reader.toString());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setNClob(int, java.io.Reader, long)
	 */
	@Override
	public void setNClob(final int parameterIndex, final Reader reader, final long length) throws SQLException {
		this.nativeObject.setNClob(parameterIndex, reader, length);
		if (isSqlLogEnabled()) {
			if (reader == null) {
				setNStringParameter(parameterIndex - 1, null);
			} else {
				setNStringParameter(parameterIndex - 1, reader.toString());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setNString(int, java.lang.String)
	 */
	@Override
	public void setNString(final int parameterIndex, final String value) throws SQLException {
		this.nativeObject.setNString(parameterIndex, value);
		if (isSqlLogEnabled()) {
			setNStringParameter(parameterIndex - 1, value);
		}
	}

	@Override
	public void setNull(final int parameterIndex, final int sqlType) throws SQLException {
		this.nativeObject.setNull(parameterIndex, sqlType);
		if (isSqlLogEnabled()) {
			parameters.set(parameterIndex - 1, null);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setNull(int, int, java.lang.String)
	 */
	@Override
	public void setNull(final int parameterIndex, final int sqlType, final String typeName) throws SQLException {
		this.nativeObject.setNull(parameterIndex, sqlType, typeName);
		if (isSqlLogEnabled()) {
			parameters.set(parameterIndex - 1, "CAST(NULL AS " + typeName + ")");
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setObject(int, java.lang.Object)
	 */
	@Override
	public void setObject(final int parameterIndex, final Object x) throws SQLException {
		try {
			this.nativeObject.setObject(parameterIndex, x);
			if (isSqlLogEnabled()) {
				setObjectParameter(parameterIndex - 1, x);
			}
		} catch (final SQLException e) {
			error(e.getMessage() + ",object=" + x, e);
			throw e;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setObject(int, java.lang.Object, int)
	 */
	@Override
	public void setObject(final int parameterIndex, final Object x, final int targetSqlType) throws SQLException {
		this.nativeObject.setObject(parameterIndex, x, targetSqlType);
		if (isSqlLogEnabled()) {
			setObjectParameter(parameterIndex - 1, x, targetSqlType);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setObject(int, java.lang.Object, int, int)
	 */
	@Override
	public void setObject(final int parameterIndex, final Object x, final int targetSqlType, final int scaleOrLength)
			throws SQLException {
		this.nativeObject.setObject(parameterIndex, x, targetSqlType, scaleOrLength);
		if (isSqlLogEnabled()) {
			setObjectParameter(parameterIndex - 1, x, targetSqlType);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setRef(int, java.sql.Ref)
	 */
	@Override
	public void setRef(final int parameterIndex, final Ref x) throws SQLException {
		this.nativeObject.setRef(parameterIndex, x);
		if (isSqlLogEnabled()) {
			if (x == null) {
				setBinaryParameter(parameterIndex - 1, null);
			} else {
				setBinaryParameter(parameterIndex - 1, x.toString());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setRowId(int, java.sql.RowId)
	 */
	@Override
	public void setRowId(final int parameterIndex, final RowId x) throws SQLException {
		this.nativeObject.setRowId(parameterIndex, x);
		if (isSqlLogEnabled()) {
			if (x == null) {
				setBinaryParameter(parameterIndex - 1, null);
			} else {
				setBinaryParameter(parameterIndex - 1, x.toString());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setSQLXML(int, java.sql.SQLXML)
	 */
	@Override
	public void setSQLXML(final int parameterIndex, final SQLXML xmlObject) throws SQLException {
		this.nativeObject.setSQLXML(parameterIndex, xmlObject);
		if (isSqlLogEnabled()) {
			if (xmlObject == null) {
				setStringParameter(parameterIndex - 1, null);
			} else {
				setStringParameter(parameterIndex - 1, xmlObject.toString());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setShort(int, short)
	 */
	@Override
	public void setShort(final int parameterIndex, final short x) throws SQLException {
		this.nativeObject.setShort(parameterIndex, x);
		if (isSqlLogEnabled()) {
			parameters.set(parameterIndex - 1, x);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setString(int, java.lang.String)
	 */
	@Override
	public void setString(final int parameterIndex, final String x) throws SQLException {
		this.nativeObject.setString(parameterIndex, x);
		if (isSqlLogEnabled()) {
			setStringParameter(parameterIndex - 1, x);
		}
	}

	@Override
	public void setTime(final int parameterIndex, final Time x) throws SQLException {
		this.nativeObject.setTime(parameterIndex, x);
		if (isSqlLogEnabled()) {
			if (x == null) {
				setStringParameter(parameterIndex - 1, null);
			} else {
				setStringParameter(parameterIndex - 1, DateUtils.format(x));
			}
		}
	}

	@Override
	public void setTime(final int parameterIndex, final Time x, final Calendar cal) throws SQLException {
		this.nativeObject.setTime(parameterIndex, x, cal);
		if (isSqlLogEnabled()) {
			if (x == null) {
				setStringParameter(parameterIndex - 1, null);
			} else {
				setStringParameter(parameterIndex - 1, DateUtils.format(x));
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setTimestamp(int, java.sql.Timestamp)
	 */
	@Override
	public void setTimestamp(final int parameterIndex, final Timestamp x) throws SQLException {
		this.nativeObject.setTimestamp(parameterIndex, x);
		if (isSqlLogEnabled()) {
			if (x == null) {
				setStringParameter(parameterIndex - 1, null);
			} else {
				setStringParameter(parameterIndex - 1, x.toString());
			}
		}
	}

	@Override
	public void setTimestamp(final int parameterIndex, final Timestamp x, final Calendar cal) throws SQLException {
		this.nativeObject.setTimestamp(parameterIndex, x, cal);
		if (isSqlLogEnabled()) {
			if (x == null) {
				setStringParameter(parameterIndex - 1, null);
			} else {
				setStringParameter(parameterIndex - 1, x.toString());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setURL(int, java.net.URL)
	 */
	@Override
	public void setURL(final int parameterIndex, final URL x) throws SQLException {
		this.nativeObject.setURL(parameterIndex, x);
		if (isSqlLogEnabled()) {
			if (x == null) {
				setStringParameter(parameterIndex - 1, null);
			} else {
				setStringParameter(parameterIndex - 1, x.toString());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.PreparedStatement#setUnicodeStream(int, java.io.InputStream,
	 * int)
	 */
	@Override
	@Deprecated
	public void setUnicodeStream(final int parameterIndex, final InputStream x, final int length) throws SQLException {
		this.nativeObject.setUnicodeStream(parameterIndex, x, length);
		if (isSqlLogEnabled()) {
			if (x == null) {
				setNStringParameter(parameterIndex - 1, null);
			} else {
				setNStringParameter(parameterIndex - 1, x.toString());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#closeOnCompletion()
	 */
	@Override
	public void closeOnCompletion() throws SQLException {
		this.nativeObject.closeOnCompletion();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Statement#isCloseOnCompletion()
	 */
	@Override
	public boolean isCloseOnCompletion() throws SQLException {
		return this.nativeObject.isCloseOnCompletion();
	}

	@Override
	public void setObject(final int parameterIndex, final Object x, final java.sql.SQLType targetSqlType,
			final int scaleOrLength) throws SQLException {
		this.nativeObject.setObject(parameterIndex, x, targetSqlType, scaleOrLength);
		if (isSqlLogEnabled()) {
			final Integer typeNumber = targetSqlType == null ? null : targetSqlType.getVendorTypeNumber();
			if (typeNumber == null) {
				setObjectParameter(parameterIndex - 1, x);
			} else {
				setObjectParameter(parameterIndex - 1, x, typeNumber);
			}
		}
	}

	@Override
	public void setObject(final int parameterIndex, final Object x, final java.sql.SQLType targetSqlType)
			throws SQLException {
		this.nativeObject.setObject(parameterIndex, x, targetSqlType);
		if (isSqlLogEnabled()) {
			final Integer typeNumber = targetSqlType == null ? null : targetSqlType.getVendorTypeNumber();
			if (typeNumber == null) {
				setObjectParameter(parameterIndex - 1, x);
			} else {
				setObjectParameter(parameterIndex - 1, x, typeNumber);
			}
		}
	}

	@Override
	public long executeLargeUpdate() throws SQLException {
		return this.nativeObject.executeLargeUpdate();
	}

}
