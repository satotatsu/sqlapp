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

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Map;

/**
 * @author satoh
 *
 * @param <T>
 */
public abstract class AbstractCallableStatement<T extends CallableStatement> extends AbstractPreparedStatement<T> implements CallableStatement {

	public AbstractCallableStatement(final T nativeObject,
			final String sql, final SqlappConnection connection) {
		super(nativeObject, sql, connection);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getArray(int)
	 */
	@Override
	public Array getArray(final int parameterIndex) throws SQLException {
		return this.nativeObject.getArray(parameterIndex);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getArray(java.lang.String)
	 */
	@Override
	public Array getArray(final String parameterName) throws SQLException {
		return this.nativeObject.getArray(parameterName);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getBigDecimal(int)
	 */
	@Override
	public BigDecimal getBigDecimal(final int parameterIndex) throws SQLException {
		return this.nativeObject.getBigDecimal(parameterIndex);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getBigDecimal(java.lang.String)
	 */
	@Override
	public BigDecimal getBigDecimal(final String parameterName) throws SQLException {
		return this.nativeObject.getBigDecimal(parameterName);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getBigDecimal(int, int)
	 */
	@SuppressWarnings("deprecation")
	@Override
	public BigDecimal getBigDecimal(final int parameterIndex, final int scale)
			throws SQLException {
		return this.nativeObject.getBigDecimal(parameterIndex, scale);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getBlob(int)
	 */
	@Override
	public Blob getBlob(final int parameterIndex) throws SQLException {
		return this.nativeObject.getBlob(parameterIndex);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getBlob(java.lang.String)
	 */
	@Override
	public Blob getBlob(final String parameterName) throws SQLException {
		return this.nativeObject.getBlob(parameterName);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getBoolean(int)
	 */
	@Override
	public boolean getBoolean(final int parameterIndex) throws SQLException {
		return this.nativeObject.getBoolean(parameterIndex);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getBoolean(java.lang.String)
	 */
	@Override
	public boolean getBoolean(final String parameterName) throws SQLException {
		return this.nativeObject.getBoolean(parameterName);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getByte(int)
	 */
	@Override
	public byte getByte(final int parameterIndex) throws SQLException {
		return this.nativeObject.getByte(parameterIndex);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getByte(java.lang.String)
	 */
	@Override
	public byte getByte(final String parameterName) throws SQLException {
		return this.nativeObject.getByte(parameterName);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getBytes(int)
	 */
	@Override
	public byte[] getBytes(final int parameterIndex) throws SQLException {
		return this.nativeObject.getBytes(parameterIndex);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getBytes(java.lang.String)
	 */
	@Override
	public byte[] getBytes(final String parameterName) throws SQLException {
		return this.nativeObject.getBytes(parameterName);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getCharacterStream(int)
	 */
	@Override
	public Reader getCharacterStream(final int parameterIndex) throws SQLException {
		return this.nativeObject.getCharacterStream(parameterIndex);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getCharacterStream(java.lang.String)
	 */
	@Override
	public Reader getCharacterStream(final String parameterName) throws SQLException {
		return this.nativeObject.getCharacterStream(parameterName);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getClob(int)
	 */
	@Override
	public Clob getClob(final int parameterIndex) throws SQLException {
		return this.nativeObject.getClob(parameterIndex);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getClob(java.lang.String)
	 */
	@Override
	public Clob getClob(final String parameterName) throws SQLException {
		return this.nativeObject.getClob(parameterName);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getDate(int)
	 */
	@Override
	public Date getDate(final int parameterIndex) throws SQLException {
		return this.nativeObject.getDate(parameterIndex);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getDate(java.lang.String)
	 */
	@Override
	public Date getDate(final String parameterName) throws SQLException {
		return this.nativeObject.getDate(parameterName);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getDate(int, java.util.Calendar)
	 */
	@Override
	public Date getDate(final int parameterIndex, final Calendar cal) throws SQLException {
		return this.nativeObject.getDate(parameterIndex, cal);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getDate(java.lang.String, java.util.Calendar)
	 */
	@Override
	public Date getDate(final String parameterName, final Calendar cal) throws SQLException {
		return this.nativeObject.getDate(parameterName, cal);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getDouble(int)
	 */
	@Override
	public double getDouble(final int parameterIndex) throws SQLException {
		return this.nativeObject.getDouble(parameterIndex);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getDouble(java.lang.String)
	 */
	@Override
	public double getDouble(final String parameterName) throws SQLException {
		return this.nativeObject.getDouble(parameterName);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getFloat(int)
	 */
	@Override
	public float getFloat(final int parameterIndex) throws SQLException {
		return this.nativeObject.getFloat(parameterIndex);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getFloat(java.lang.String)
	 */
	@Override
	public float getFloat(final String parameterName) throws SQLException {
		return this.nativeObject.getFloat(parameterName);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getInt(int)
	 */
	@Override
	public int getInt(final int parameterIndex) throws SQLException {
		return this.nativeObject.getInt(parameterIndex);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getInt(java.lang.String)
	 */
	@Override
	public int getInt(final String parameterName) throws SQLException {
		return this.nativeObject.getInt(parameterName);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getLong(int)
	 */
	@Override
	public long getLong(final int parameterIndex) throws SQLException {
		return this.nativeObject.getLong(parameterIndex);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getLong(java.lang.String)
	 */
	@Override
	public long getLong(final String parameterName) throws SQLException {
		return this.nativeObject.getLong(parameterName);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getNCharacterStream(int)
	 */
	@Override
	public Reader getNCharacterStream(final int parameterIndex) throws SQLException {
		return this.nativeObject.getNCharacterStream(parameterIndex);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getNCharacterStream(java.lang.String)
	 */
	@Override
	public Reader getNCharacterStream(final String parameterName) throws SQLException {
		return this.nativeObject.getNCharacterStream(parameterName);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getNClob(int)
	 */
	@Override
	public NClob getNClob(final int parameterIndex) throws SQLException {
		return this.nativeObject.getNClob(parameterIndex);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getNClob(java.lang.String)
	 */
	@Override
	public NClob getNClob(final String parameterName) throws SQLException {
		return this.nativeObject.getNClob(parameterName);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getNString(int)
	 */
	@Override
	public String getNString(final int parameterIndex) throws SQLException {
		return this.nativeObject.getNString(parameterIndex);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getNString(java.lang.String)
	 */
	@Override
	public String getNString(final String parameterName) throws SQLException {
		return this.nativeObject.getNString(parameterName);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getObject(int)
	 */
	@Override
	public Object getObject(final int parameterIndex) throws SQLException {
		return this.nativeObject.getObject(parameterIndex);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getObject(java.lang.String)
	 */
	@Override
	public Object getObject(final String parameterName) throws SQLException {
		return this.nativeObject.getObject(parameterName);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getObject(int, java.util.Map)
	 */
	@Override
	public Object getObject(final int parameterIndex, final Map<String, Class<?>> map)
			throws SQLException {
		return this.nativeObject.getObject(parameterIndex, map);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getObject(java.lang.String, java.util.Map)
	 */
	@Override
	public Object getObject(final String parameterName, final Map<String, Class<?>> map)
			throws SQLException {
		return this.nativeObject.getObject(parameterName, map);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getRef(int)
	 */
	@Override
	public Ref getRef(final int parameterIndex) throws SQLException {
		return this.nativeObject.getRef(parameterIndex);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getRef(java.lang.String)
	 */
	@Override
	public Ref getRef(final String parameterName) throws SQLException {
		return this.nativeObject.getRef(parameterName);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getRowId(int)
	 */
	@Override
	public RowId getRowId(final int parameterIndex) throws SQLException {
		return this.nativeObject.getRowId(parameterIndex);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getRowId(java.lang.String)
	 */
	@Override
	public RowId getRowId(final String parameterName) throws SQLException {
		return this.nativeObject.getRowId(parameterName);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getSQLXML(int)
	 */
	@Override
	public SQLXML getSQLXML(final int parameterIndex) throws SQLException {
		return this.nativeObject.getSQLXML(parameterIndex);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getSQLXML(java.lang.String)
	 */
	@Override
	public SQLXML getSQLXML(final String parameterName) throws SQLException {
		return this.nativeObject.getSQLXML(parameterName);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getShort(int)
	 */
	@Override
	public short getShort(final int parameterIndex) throws SQLException {
		return this.nativeObject.getShort(parameterIndex);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getShort(java.lang.String)
	 */
	@Override
	public short getShort(final String parameterName) throws SQLException {
		return this.nativeObject.getShort(parameterName);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getString(int)
	 */
	@Override
	public String getString(final int parameterIndex) throws SQLException {
		return this.nativeObject.getString(parameterIndex);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getString(java.lang.String)
	 */
	@Override
	public String getString(final String parameterName) throws SQLException {
		return this.nativeObject.getString(parameterName);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getTime(int)
	 */
	@Override
	public Time getTime(final int parameterIndex) throws SQLException {
		return this.nativeObject.getTime(parameterIndex);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getTime(java.lang.String)
	 */
	@Override
	public Time getTime(final String parameterName) throws SQLException {
		return this.nativeObject.getTime(parameterName);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getTime(int, java.util.Calendar)
	 */
	@Override
	public Time getTime(final int parameterIndex, final Calendar cal) throws SQLException {
		return this.nativeObject.getTime(parameterIndex, cal);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getTime(java.lang.String, java.util.Calendar)
	 */
	@Override
	public Time getTime(final String parameterName, final Calendar cal) throws SQLException {
		return this.nativeObject.getTime(parameterName, cal);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getTimestamp(int)
	 */
	@Override
	public Timestamp getTimestamp(final int parameterIndex) throws SQLException {
		return this.nativeObject.getTimestamp(parameterIndex);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getTimestamp(java.lang.String)
	 */
	@Override
	public Timestamp getTimestamp(final String parameterName) throws SQLException {
		return this.nativeObject.getTimestamp(parameterName);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getTimestamp(int, java.util.Calendar)
	 */
	@Override
	public Timestamp getTimestamp(final int parameterIndex, final Calendar cal)
			throws SQLException {
		return this.nativeObject.getTimestamp(parameterIndex, cal);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getTimestamp(java.lang.String, java.util.Calendar)
	 */
	@Override
	public Timestamp getTimestamp(final String parameterName, final Calendar cal)
			throws SQLException {
		return this.nativeObject.getTimestamp(parameterName, cal);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getURL(int)
	 */
	@Override
	public URL getURL(final int parameterIndex) throws SQLException {
		return this.nativeObject.getURL(parameterIndex);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getURL(java.lang.String)
	 */
	@Override
	public URL getURL(final String parameterName) throws SQLException {
		return this.nativeObject.getURL(parameterName);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#registerOutParameter(int, int)
	 */
	@Override
	public void registerOutParameter(final int parameterIndex, final int sqlType)
			throws SQLException {
		this.nativeObject.registerOutParameter(parameterIndex, sqlType);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#registerOutParameter(java.lang.String, int)
	 */
	@Override
	public void registerOutParameter(final String parameterName, final int sqlType)
			throws SQLException {
		this.nativeObject.registerOutParameter(parameterName, sqlType);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#registerOutParameter(int, int, int)
	 */
	@Override
	public void registerOutParameter(final int parameterIndex, final int sqlType, final int scale)
			throws SQLException {
		this.nativeObject.registerOutParameter(parameterIndex, sqlType, scale);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#registerOutParameter(int, int, java.lang.String)
	 */
	@Override
	public void registerOutParameter(final int parameterIndex, final int sqlType,
			final String typeName) throws SQLException {
		this.nativeObject.registerOutParameter(parameterIndex, sqlType, typeName);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#registerOutParameter(java.lang.String, int, int)
	 */
	@Override
	public void registerOutParameter(final String parameterName, final int sqlType,
			final int scale) throws SQLException {
		this.nativeObject.registerOutParameter(parameterName, sqlType, scale);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#registerOutParameter(java.lang.String, int, java.lang.String)
	 */
	@Override
	public void registerOutParameter(final String parameterName, final int sqlType,
			final String typeName) throws SQLException {
		this.nativeObject.registerOutParameter(parameterName, sqlType, typeName);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setAsciiStream(java.lang.String, java.io.InputStream)
	 */
	@Override
	public void setAsciiStream(final String parameterName, final InputStream x)
			throws SQLException {
		this.nativeObject.setAsciiStream(parameterName, x);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setAsciiStream(java.lang.String, java.io.InputStream, int)
	 */
	@Override
	public void setAsciiStream(final String parameterName, final InputStream x, final int length)
			throws SQLException {
		this.nativeObject.setAsciiStream(parameterName, x, length);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setAsciiStream(java.lang.String, java.io.InputStream, long)
	 */
	@Override
	public void setAsciiStream(final String parameterName, final InputStream x, final long length)
			throws SQLException {
		this.nativeObject.setAsciiStream(parameterName, x, length);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setBigDecimal(java.lang.String, java.math.BigDecimal)
	 */
	@Override
	public void setBigDecimal(final String parameterName, final BigDecimal x)
			throws SQLException {
		this.nativeObject.setBigDecimal(parameterName, x);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setBinaryStream(java.lang.String, java.io.InputStream)
	 */
	@Override
	public void setBinaryStream(final String parameterName, final InputStream x)
			throws SQLException {
		this.nativeObject.setBinaryStream(parameterName, x);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setBinaryStream(java.lang.String, java.io.InputStream, int)
	 */
	@Override
	public void setBinaryStream(final String parameterName, final InputStream x, final int length)
			throws SQLException {
		this.nativeObject.setBinaryStream(parameterName, x, length);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setBinaryStream(java.lang.String, java.io.InputStream, long)
	 */
	@Override
	public void setBinaryStream(final String parameterName, final InputStream x, final long length)
			throws SQLException {
		this.nativeObject.setBinaryStream(parameterName, x, length);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setBlob(java.lang.String, java.sql.Blob)
	 */
	@Override
	public void setBlob(final String parameterName, final Blob x) throws SQLException {
		this.nativeObject.setBlob(parameterName, x);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setBlob(java.lang.String, java.io.InputStream)
	 */
	@Override
	public void setBlob(final String parameterName, final InputStream inputStream)
			throws SQLException {
		this.nativeObject.setBlob(parameterName, inputStream);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setBlob(java.lang.String, java.io.InputStream, long)
	 */
	@Override
	public void setBlob(final String parameterName, final InputStream inputStream,
			final long length) throws SQLException {
		this.nativeObject.setBlob(parameterName, inputStream, length);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setBoolean(java.lang.String, boolean)
	 */
	@Override
	public void setBoolean(final String parameterName, final boolean x) throws SQLException {
		this.nativeObject.setBoolean(parameterName, x);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setByte(java.lang.String, byte)
	 */
	@Override
	public void setByte(final String parameterName, final byte x) throws SQLException {
		this.nativeObject.setByte(parameterName, x);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setBytes(java.lang.String, byte[])
	 */
	@Override
	public void setBytes(final String parameterName, final byte[] x) throws SQLException {
		this.nativeObject.setBytes(parameterName, x);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setCharacterStream(java.lang.String, java.io.Reader)
	 */
	@Override
	public void setCharacterStream(final String parameterName, final Reader reader)
			throws SQLException {
		this.nativeObject.setCharacterStream(parameterName, reader);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setCharacterStream(java.lang.String, java.io.Reader, int)
	 */
	@Override
	public void setCharacterStream(final String parameterName, final Reader reader,
			final int length) throws SQLException {
		this.nativeObject.setCharacterStream(parameterName, reader, length);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setCharacterStream(java.lang.String, java.io.Reader, long)
	 */
	@Override
	public void setCharacterStream(final String parameterName, final Reader reader,
			final long length) throws SQLException {
		this.nativeObject.setCharacterStream(parameterName, reader, length);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setClob(java.lang.String, java.sql.Clob)
	 */
	@Override
	public void setClob(final String parameterName, final Clob x) throws SQLException {
		this.nativeObject.setClob(parameterName, x);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setClob(java.lang.String, java.io.Reader)
	 */
	@Override
	public void setClob(final String parameterName, final Reader reader)
			throws SQLException {
		this.nativeObject.setClob(parameterName, reader);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setClob(java.lang.String, java.io.Reader, long)
	 */
	@Override
	public void setClob(final String parameterName, final Reader reader, final long length)
			throws SQLException {
		this.nativeObject.setClob(parameterName, reader, length);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setDate(java.lang.String, java.sql.Date)
	 */
	@Override
	public void setDate(final String parameterName, final Date x) throws SQLException {
		this.nativeObject.setDate(parameterName, x);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setDate(java.lang.String, java.sql.Date, java.util.Calendar)
	 */
	@Override
	public void setDate(final String parameterName, final Date x, final Calendar cal)
			throws SQLException {
		this.nativeObject.setDate(parameterName, x, cal);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setDouble(java.lang.String, double)
	 */
	@Override
	public void setDouble(final String parameterName, final double x) throws SQLException {
		this.nativeObject.setDouble(parameterName, x);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setFloat(java.lang.String, float)
	 */
	@Override
	public void setFloat(final String parameterName, final float x) throws SQLException {
		this.nativeObject.setFloat(parameterName, x);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setInt(java.lang.String, int)
	 */
	@Override
	public void setInt(final String parameterName, final int x) throws SQLException {
		this.nativeObject.setInt(parameterName, x);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setLong(java.lang.String, long)
	 */
	@Override
	public void setLong(final String parameterName, final long x) throws SQLException {
		this.nativeObject.setLong(parameterName, x);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setNCharacterStream(java.lang.String, java.io.Reader)
	 */
	@Override
	public void setNCharacterStream(final String parameterName, final Reader value)
			throws SQLException {
		this.nativeObject.setNCharacterStream(parameterName, value);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setNCharacterStream(java.lang.String, java.io.Reader, long)
	 */
	@Override
	public void setNCharacterStream(final String parameterName, final Reader value,
			final long length) throws SQLException {
		this.nativeObject.setNCharacterStream(parameterName, value, length);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setNClob(java.lang.String, java.sql.NClob)
	 */
	@Override
	public void setNClob(final String parameterName, final NClob value) throws SQLException {
		this.nativeObject.setNClob(parameterName, value);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setNClob(java.lang.String, java.io.Reader)
	 */
	@Override
	public void setNClob(final String parameterName, final Reader reader)
			throws SQLException {
		this.nativeObject.setNClob(parameterName, reader);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setNClob(java.lang.String, java.io.Reader, long)
	 */
	@Override
	public void setNClob(final String parameterName, final Reader reader, final long length)
			throws SQLException {
		this.nativeObject.setNClob(parameterName, reader, length);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setNString(java.lang.String, java.lang.String)
	 */
	@Override
	public void setNString(final String parameterName, final String value)
			throws SQLException {
		this.nativeObject.setNString(parameterName, value);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setNull(java.lang.String, int)
	 */
	@Override
	public void setNull(final String parameterName, final int sqlType) throws SQLException {
		this.nativeObject.setNull(parameterName, sqlType);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setNull(java.lang.String, int, java.lang.String)
	 */
	@Override
	public void setNull(final String parameterName, final int sqlType, final String typeName)
			throws SQLException {
		this.nativeObject.setNull(parameterName, sqlType, typeName);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setObject(java.lang.String, java.lang.Object)
	 */
	@Override
	public void setObject(final String parameterName, final Object x) throws SQLException {
		this.nativeObject.setObject(parameterName, x);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setObject(java.lang.String, java.lang.Object, int)
	 */
	@Override
	public void setObject(final String parameterName, final Object x, final int targetSqlType)
			throws SQLException {
		this.nativeObject.setObject(parameterName, x, targetSqlType);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setObject(java.lang.String, java.lang.Object, int, int)
	 */
	@Override
	public void setObject(final String parameterName, final Object x, final int targetSqlType,
			final int scale) throws SQLException {
		this.nativeObject.setObject(parameterName, x, targetSqlType, scale);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setRowId(java.lang.String, java.sql.RowId)
	 */
	@Override
	public void setRowId(final String parameterName, final RowId x) throws SQLException {
		this.nativeObject.setRowId(parameterName, x);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setSQLXML(java.lang.String, java.sql.SQLXML)
	 */
	@Override
	public void setSQLXML(final String parameterName, final SQLXML xmlObject)
			throws SQLException {
		this.nativeObject.setSQLXML(parameterName, xmlObject);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setShort(java.lang.String, short)
	 */
	@Override
	public void setShort(final String parameterName, final short x) throws SQLException {
		this.nativeObject.setShort(parameterName, x);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setString(java.lang.String, java.lang.String)
	 */
	@Override
	public void setString(final String parameterName, final String x) throws SQLException {
		this.nativeObject.setString(parameterName, x);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setTime(java.lang.String, java.sql.Time)
	 */
	@Override
	public void setTime(final String parameterName, final Time x) throws SQLException {
		this.nativeObject.setTime(parameterName, x);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setTime(java.lang.String, java.sql.Time, java.util.Calendar)
	 */
	@Override
	public void setTime(final String parameterName, final Time x, final Calendar cal)
			throws SQLException {
		this.nativeObject.setTime(parameterName, x, cal);
	}

	@Override
	public void setTimestamp(final String parameterName, final Timestamp x)
			throws SQLException {
		this.nativeObject.setTimestamp(parameterName, x);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setTimestamp(java.lang.String, java.sql.Timestamp, java.util.Calendar)
	 */
	@Override
	public void setTimestamp(final String parameterName, final Timestamp x, final Calendar cal)
			throws SQLException {
		this.nativeObject.setTimestamp(parameterName, x, cal);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#setURL(java.lang.String, java.net.URL)
	 */
	@Override
	public void setURL(final String parameterName, final URL val) throws SQLException {
		this.nativeObject.setURL(parameterName, val);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#wasNull()
	 */
	@Override
	public boolean wasNull() throws SQLException {
		return this.nativeObject.wasNull();
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getObject(int, java.lang.Class)
	 */
	@Override
	public <TT> TT getObject(final int parameterIndex, final Class<TT> type)
			throws SQLException {
		return nativeObject.getObject(parameterIndex, type);
	}

	/* (non-Javadoc)
	 * @see java.sql.CallableStatement#getObject(java.lang.String, java.lang.Class)
	 */
	@Override
	public <TT> TT getObject(final String parameterName, final Class<TT> type)
			throws SQLException {
		return nativeObject.getObject(parameterName, type);
	}

	/* (non-Javadoc)
	 * @see java.sql.Statement#closeOnCompletion()
	 */
	@Override
	public void closeOnCompletion() throws SQLException {
		nativeObject.closeOnCompletion();
	}

	/* (non-Javadoc)
	 * @see java.sql.Statement#isCloseOnCompletion()
	 */
	@Override
	public boolean isCloseOnCompletion() throws SQLException {
		return nativeObject.isCloseOnCompletion();
	}
	

	@Override
	public void setObject(final String parameterName, final Object x, final java.sql.SQLType targetSqlType,
	         final int scaleOrLength) throws SQLException {
		nativeObject.setObject(scaleOrLength, x, scaleOrLength);
	}
	
	@Override
	public void setObject(final String parameterName, final Object x, final java.sql.SQLType targetSqlType)
	    throws SQLException {
	    throw new SQLFeatureNotSupportedException("setObject not implemented");
	}
	
	@Override
	public void registerOutParameter(final int parameterIndex, final java.sql.SQLType sqlType)
	    throws SQLException {
	    throw new SQLFeatureNotSupportedException("registerOutParameter not implemented");
	}
	
	@Override
	public void registerOutParameter(final int parameterIndex, final java.sql.SQLType sqlType,
	        final int scale) throws SQLException {
	    throw new SQLFeatureNotSupportedException("registerOutParameter not implemented");
	}
	@Override
	public void registerOutParameter (final int parameterIndex, final java.sql.SQLType sqlType,
	        final String typeName) throws SQLException {
	    throw new SQLFeatureNotSupportedException("registerOutParameter not implemented");
	}
	
	@Override
	public void registerOutParameter(final String parameterName, final java.sql.SQLType sqlType)
	    throws SQLException {
	    throw new SQLFeatureNotSupportedException("registerOutParameter not implemented");
	}
	
	@Override
	public void registerOutParameter(final String parameterName, final java.sql.SQLType sqlType,
	        final int scale) throws SQLException {
	    throw new SQLFeatureNotSupportedException("registerOutParameter not implemented");
	}
	
	@Override
	public void registerOutParameter (final String parameterName, final java.sql.SQLType sqlType,
	        final String typeName) throws SQLException {
	    throw new SQLFeatureNotSupportedException("registerOutParameter not implemented");
	}

}
