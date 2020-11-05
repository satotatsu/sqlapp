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
package com.sqlapp.data.db.datatype;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

import com.sqlapp.data.converter.Converter;
import com.sqlapp.data.converter.UUIDConverter;
import com.sqlapp.util.BinaryUtils;

import static com.sqlapp.util.CommonUtils.*;
/**
 * UUIDを表す型
 * @author satoh
 *
 */
public class UUIDType extends AbstractNoSizeType<UUIDType>{

	/** serialVersionUID */
	private static final long serialVersionUID = -8658816953027318522L;
	/**
	 * UUIDのコンバータ
	 */
	private UUIDConverter converter=new UUIDConverter();
	/**
	 * コンストラクタ
	 */
	public UUIDType(){
		this(DataType.UUID.getTypeName());
	}
	
	protected UUIDType(String dataTypeName){
		this.setDataType(DataType.UUID);
		this.initialize(dataTypeName);
		this.setJdbcTypeHandler(new DefaultJdbcTypeHandler(this.getDataType().getJdbcType(), converter));
		this.setUnsigned(true);
		this.setOctetSize(16L);
	}

	/**
	 * UUIDをStringとして扱う設定
	 */
	public UUIDType setAsVarcharType(){
		this.setJdbcTypeHandler(new UuidVarcharTypeHandler(this.getDataType().getJdbcType(), converter));
		this.setCreateFormat("VARCHAR(36)");
		this.setOctetSize(36L);
		setLiteral("'", "'");
		setDefaultValueLiteral("''");
		return this;
	}

	/**
	 * UUIDをBinaryとして扱う設定
	 */
	public UUIDType setAsBinaryType(){
		this.setJdbcTypeHandler(new UuidBinaryTypeHandler(this.getDataType().getJdbcType(), converter));
		this.setOctetSize(16L);
		return this;
	}

	/**
	 * UUIDを文字型としてJDBCで扱うためのハンドラー
	 * @author satoh
	 *
	 */
	private static class UuidVarcharTypeHandler extends DefaultJdbcTypeHandler{
		public UuidVarcharTypeHandler(java.sql.JDBCType jdbcType, Converter<?> converter) {
			super(jdbcType, converter);
		}
		/** serialVersionUID */
		private static final long serialVersionUID = -3446371652551511555L;
		@Override
		public void setObject(PreparedStatement stmt, int parameterIndex
				, Object x) throws SQLException{
			if (x==null){
				stmt.setNull(parameterIndex, java.sql.Types.VARCHAR);
				return;
			}
			UUID uuid=cast(this.statementConverter.convertObject(x));
			stmt.setString(parameterIndex, uuid.toString());
		}
	}

	/**
	 * UUIDをBINARY型としてJDBCで扱うためのハンドラー
	 * @author satoh
	 *
	 */
	private static  class UuidBinaryTypeHandler extends DefaultJdbcTypeHandler{
		public UuidBinaryTypeHandler(java.sql.JDBCType jdbcType, Converter<?> converter) {
			super(jdbcType, converter);
		}
		/** serialVersionUID */
		private static final long serialVersionUID = -3446371652551511555L;
		@Override
		public void setObject(PreparedStatement stmt, int parameterIndex
				, Object x) throws SQLException{
			if (x==null){
				stmt.setNull(parameterIndex, java.sql.Types.BINARY);
				return;
			}
			UUID uuid=cast(this.statementConverter.convertObject(x));
			stmt.setObject(parameterIndex, BinaryUtils.toBinary(uuid), java.sql.Types.BINARY);
		}
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.datatype.DbDataType#hashCode()
	 */
	@Override
	public int hashCode(){
		return super.hashCode();
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.datatype.DbDataType#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (!super.equals(obj)){
			return false;
		}
		if (!(obj instanceof UUIDType)){
			return false;
		}
		return true;
	}
}
