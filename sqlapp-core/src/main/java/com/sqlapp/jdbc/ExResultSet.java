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
import java.sql.Date;
import java.sql.NClob;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Set;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.util.CaseInsensitiveGetMap;
import com.sqlapp.util.CommonUtils;

public class ExResultSet extends AbstractResultSet<ResultSet,Object>{

	private CaseInsensitiveGetMap<Column> metadataMap=new CaseInsensitiveGetMap<Column>();

	public ExResultSet(ResultSet nativeObject, Object parentObject) {
		super(nativeObject, parentObject);
	}

	public ExResultSet(ResultSet nativeObject) {
		super(nativeObject, null);
	}

	private boolean checkColumns=true;
	


	/**
	 * @return the checkColumns
	 */
	public boolean isCheckColumns() {
		return checkColumns;
	}

	/**
	 * @param checkColumns the checkColumns to set
	 */
	public void setCheckColumns(boolean checkColumns) {
		this.checkColumns = checkColumns;
	}

	protected CaseInsensitiveGetMap<Column> getMetadataMap() throws SQLException{
		if (!metadataMap.isEmpty()){
			return metadataMap;
		}
		ResultSetMetaData metadata=this.getNativeObject().getMetaData();
		int count=metadata.getColumnCount();
		for(int i=1;i<=count;i++){
			String name=metadata.getColumnLabel(i);
			if (name==null){
				name=metadata.getColumnName(i);
			}
			Column column=new Column(name);
			column.setTableName(metadata.getTableName(i));
			column.setSchemaName(metadata.getSchemaName(i));
			if (metadata.isNullable(i)==ResultSetMetaData.columnNoNulls){
				column.setNotNull(true);
			}if (metadata.isNullable(i)==ResultSetMetaData.columnNullable){
				column.setNotNull(false);
			}
			metadataMap.put(name, column);
		}
		return metadataMap;
	}

	private Set<String> errorColumns=CommonUtils.set();
	
	private boolean contains(String columnLabel) throws SQLException{
		if(getMetadataMap().get(columnLabel)!=null){
			return true;
		} else{
			if (checkColumns){
				if (errorColumns.contains(columnLabel)){
					return false;
				}
				this.warn("columnLabel does not exists. columnLabel=["+columnLabel+"].");
				errorColumns.add(columnLabel);
				return false;
			} else{
				return true;
			}
		}
	}
	

	/* (non-Javadoc)
	 * @see java.sql.ResultSet#getString(java.lang.String)
	 */
	@Override
	public String getString(String columnLabel) throws SQLException {
		if (!contains(columnLabel)){
			return null;
		}
		return nativeObject.getString(columnLabel);
	}

	/* (non-Javadoc)
	 * @see java.sql.ResultSet#getBoolean(java.lang.String)
	 */
	@Override
	public boolean getBoolean(String columnLabel) throws SQLException {
		if (!contains(columnLabel)){
			return false;
		}
		return nativeObject.getBoolean(columnLabel);
	}

	public Boolean getBooleanValue(String columnLabel) throws SQLException {
		if (!contains(columnLabel)){
			return null;
		}
		boolean ret=nativeObject.getBoolean(columnLabel);
		if (wasNull()){
			return null;
		}
		return ret;
	}

	/* (non-Javadoc)
	 * @see java.sql.ResultSet#getByte(java.lang.String)
	 */
	@Override
	public byte getByte(String columnLabel) throws SQLException {
		if (!contains(columnLabel)){
			return (byte)0;
		}
		return nativeObject.getByte(columnLabel);
	}

	public Byte getByteValue(String columnLabel) throws SQLException {
		if (!contains(columnLabel)){
			return null;
		}
		byte ret=nativeObject.getByte(columnLabel);
		if (wasNull()){
			return null;
		}
		return ret;
	}

	/* (non-Javadoc)
	 * @see java.sql.ResultSet#getShort(java.lang.String)
	 */
	@Override
	public short getShort(String columnLabel) throws SQLException {
		if (!contains(columnLabel)){
			return (short)0;
		}
		short val=nativeObject.getShort(columnLabel);
		return val;
	}
	
	public Short getShortValue(String columnLabel) throws SQLException {
		if (!contains(columnLabel)){
			return null;
		}
		short ret=nativeObject.getShort(columnLabel);
		if (wasNull()){
			return null;
		}
		return ret;
	}

	/* (non-Javadoc)
	 * @see java.sql.ResultSet#getInt(java.lang.String)
	 */
	@Override
	public int getInt(String columnLabel) throws SQLException {
		if (!contains(columnLabel)){
			return 0;
		}
		int val=nativeObject.getInt(columnLabel);
		return val;
	}

	public Integer getInteger(String columnLabel) throws SQLException {
		if (!contains(columnLabel)){
			return null;
		}
		int ret=nativeObject.getInt(columnLabel);
		if (wasNull()){
			return null;
		}
		return ret;
	}
	
	@Override
	public long getLong(String columnLabel) throws SQLException {
		if (!contains(columnLabel)){
			return 0L;
		}
		long val=nativeObject.getLong(columnLabel);
		return val;
	}

	public Long getLongValue(String columnLabel) throws SQLException {
		if (!contains(columnLabel)){
			return null;
		}
		long ret=nativeObject.getLong(columnLabel);
		if (wasNull()){
			return null;
		}
		return ret;
	}

	public Float getFloatValue(String columnLabel) throws SQLException {
		if (!contains(columnLabel)){
			return null;
		}
		Float ret=nativeObject.getFloat(columnLabel);
		if (wasNull()){
			return null;
		}
		return ret;
	}

	public Double getDoubleValue(String columnLabel) throws SQLException {
		if (!contains(columnLabel)){
			return null;
		}
		Double ret=nativeObject.getDouble(columnLabel);
		if (wasNull()){
			return null;
		}
		return ret;
	}

	@Override
	public float getFloat(String columnLabel) throws SQLException {
		if (!contains(columnLabel)){
			return 0f;
		}
		float val=nativeObject.getFloat(columnLabel);
		return val;
	}

	@Override
	public double getDouble(String columnLabel) throws SQLException {
		if (!contains(columnLabel)){
			return 0.0;
		}
		double val=nativeObject.getDouble(columnLabel);
		return val;
	}

	/* (non-Javadoc)
	 * @see java.sql.ResultSet#getBigDecimal(java.lang.String, int)
	 */
	@Deprecated
	@Override
	public BigDecimal getBigDecimal(String columnLabel, int scale)
			throws SQLException {
		if (!contains(columnLabel)){
			return null;
		}
		return nativeObject.getBigDecimal(columnLabel, scale);
	}

	/* (non-Javadoc)
	 * @see java.sql.ResultSet#getBytes(java.lang.String)
	 */
	@Override
	public byte[] getBytes(String columnLabel) throws SQLException {
		if (!contains(columnLabel)){
			return null;
		}
		return nativeObject.getBytes(columnLabel);
	}

	/* (non-Javadoc)
	 * @see java.sql.ResultSet#getDate(java.lang.String)
	 */
	@Override
	public Date getDate(String columnLabel) throws SQLException {
		if (!contains(columnLabel)){
			return null;
		}
		return nativeObject.getDate(columnLabel);
	}

	/* (non-Javadoc)
	 * @see java.sql.ResultSet#getTime(java.lang.String)
	 */
	@Override
	public Time getTime(String columnLabel) throws SQLException {
		if (!contains(columnLabel)){
			return null;
		}
		return nativeObject.getTime(columnLabel);
	}

	/* (non-Javadoc)
	 * @see java.sql.ResultSet#getTimestamp(java.lang.String)
	 */
	@Override
	public Timestamp getTimestamp(String columnLabel) throws SQLException {
		if (!contains(columnLabel)){
			return null;
		}
		return nativeObject.getTimestamp(columnLabel);
	}

	/* (non-Javadoc)
	 * @see java.sql.ResultSet#getAsciiStream(java.lang.String)
	 */
	@Override
	public InputStream getAsciiStream(String columnLabel) throws SQLException {
		if (!contains(columnLabel)){
			return null;
		}
		return nativeObject.getAsciiStream(columnLabel);
	}

	/* (non-Javadoc)
	 * @see java.sql.ResultSet#getUnicodeStream(java.lang.String)
	 */
	@Deprecated
	@Override
	public InputStream getUnicodeStream(String columnLabel) throws SQLException {
		if (!contains(columnLabel)){
			return null;
		}
		return nativeObject.getUnicodeStream(columnLabel);
	}

	/* (non-Javadoc)
	 * @see java.sql.ResultSet#getBinaryStream(java.lang.String)
	 */
	@Override
	public InputStream getBinaryStream(String columnLabel) throws SQLException {
		if (!contains(columnLabel)){
			return null;
		}
		return nativeObject.getBinaryStream(columnLabel);
	}

	@Override
	public Object getObject(String columnLabel) throws SQLException {
		if (!contains(columnLabel)){
			return null;
		}
		return nativeObject.getObject(columnLabel);
	}

	@Override
	public Reader getCharacterStream(String columnLabel) throws SQLException {
		if (!contains(columnLabel)){
			return null;
		}
		return nativeObject.getCharacterStream(columnLabel);
	}

	/* (non-Javadoc)
	 * @see java.sql.ResultSet#getBigDecimal(java.lang.String)
	 */
	@Override
	public BigDecimal getBigDecimal(String columnLabel) throws SQLException {
		if (!contains(columnLabel)){
			return null;
		}
		return nativeObject.getBigDecimal(columnLabel);
	}

	@Override
	public NClob getNClob(String columnLabel) throws SQLException {
		if (!contains(columnLabel)){
			return null;
		}
		return nativeObject.getNClob(columnLabel);
	}

	@Override
	public SQLXML getSQLXML(String columnLabel) throws SQLException {
		if (!contains(columnLabel)){
			return null;
		}
		return nativeObject.getSQLXML(columnLabel);
	}
	
	@Override
	public <TT> TT getObject(String columnLabel, Class<TT> type)
			throws SQLException {
		if (!contains(columnLabel)){
			return null;
		}
		TT object= nativeObject.getObject(columnLabel, type);
		if (this.wasNull()){
			return null;
		}
		return object;
	}
}
