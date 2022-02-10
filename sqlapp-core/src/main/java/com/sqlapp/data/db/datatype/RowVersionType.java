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

import com.sqlapp.data.converter.ByteArrayConverter;
import com.sqlapp.data.converter.Converter;

/**
 * VERSION_BINARYを表す型
 * @author satoh
 *
 */
public class RowVersionType extends AbstractNoSizeType<RowVersionType>{

	/** serialVersionUID */
	private static final long serialVersionUID = -8658816953027318522L;
	/**
	 * コンバータ
	 */
	protected Converter<byte[]> converter=new ByteArrayConverter();
	/**
	 * コンストラクタ
	 */
	public RowVersionType(){
		this(DataType.ROWVERSION.getTypeName());
	}
	
	
	/**
	 * コンストラクタ
	 */
	public RowVersionType(String dataTypeName){
		this.setDataType(DataType.ROWVERSION);
		initialize(dataTypeName);
		this.setJdbcTypeHandler(new VersionBinaryTypeHandler(this.getDataType().getJdbcType(), converter));
		this.setOctetSize(8L);
		this.setConcurrencyType(true);
	}

	/**
	 * VERSION_BINARY用のJDBCで扱うためのハンドラー
	 * @author satoh
	 */
	private static class VersionBinaryTypeHandler extends DefaultJdbcTypeHandler{
		public VersionBinaryTypeHandler(java.sql.JDBCType jdbcType, Converter<byte[]> converter) {
			super(jdbcType, converter);
		}
		/** serialVersionUID */
		private static final long serialVersionUID = -3446371652551511555L;
		@Override
		public void setObject(PreparedStatement stmt, int parameterIndex
				, Object x) throws SQLException{
			//値の更新は不可
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
		if (obj==this){
			return true;
		}
		if (!(obj instanceof RowVersionType)){
			return false;
		}
		if (super.equals(obj)){
			return false;
		}
		return true;
	}
}
