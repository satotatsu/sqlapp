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

package com.sqlapp.data.db.datatype;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.sqlapp.data.converter.BooleanConverter;
import com.sqlapp.data.converter.Converter;

import static com.sqlapp.util.CommonUtils.*;

/**
 * BITを表す型
 * @author satoh
 *
 */
public class BitType extends AbstractNoSizeType<BitType>{

	/** serialVersionUID */
	private static final long serialVersionUID = -8658816953027318522L;
	/**
	 * Booleanのコンバータ
	 */
	protected Converter<Boolean> converter=new BooleanConverter();
	/**
	 * コンストラクタ
	 */
	public BitType(){
		this(DataType.BIT.getTypeName());
	}

	/**
	 * コンストラクタ
	 */
	public BitType(String dataTypeName){
		this.setDataType(DataType.BIT);
		initialize(dataTypeName);
		this.setUnsigned(true);
	}
	
	/**
	 * BITをStringとして扱う設定
	 */
	public BitType setAsStringType(){
		this.setJdbcTypeHandler(new BitStringTypeHandler(this.getDataType().getJdbcType(), converter));
		setDefaultValueLiteral("'0'");
		return this;
	}

	/**
	 * BITを数値として扱う設定
	 */
	public BitType setAsNumericType(){
		this.setJdbcTypeHandler(new BitNumericTypeHandler(this.getDataType().getJdbcType(), converter));
		setDefaultValueLiteral("0");
		return this;
	}

	/**
	 * BITを文字型としてJDBCで扱うためのハンドラー
	 * @author satoh
	 */
	static class BitStringTypeHandler extends DefaultJdbcTypeHandler{
		public BitStringTypeHandler(java.sql.JDBCType jdbcType, Converter<Boolean> converter) {
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
			Boolean bool=cast(this.statementConverter.convertObject(x));
			if (bool.booleanValue()){
				stmt.setString(parameterIndex, "1");
			} else{
				stmt.setString(parameterIndex, "0");
			}
		}
	}

	/**
	 * BITを数値型としてJDBCで扱うためのハンドラー
	 * @author satoh
	 */
	static class BitNumericTypeHandler extends DefaultJdbcTypeHandler{
		public BitNumericTypeHandler(java.sql.JDBCType jdbcType, Converter<Boolean> converter) {
			super(jdbcType, converter);
		}
		/** serialVersionUID */
		private static final long serialVersionUID = -3446371652551511555L;
		@Override
		public void setObject(PreparedStatement stmt, int parameterIndex
				, Object x) throws SQLException{
			if (x==null){
				stmt.setNull(parameterIndex, java.sql.Types.TINYINT);
				return;
			}
			Boolean bool=cast(this.statementConverter.convertObject(x));
			if (bool.booleanValue()){
				byte s=1;
				stmt.setByte(parameterIndex, s);
			} else{
				byte s=0;
				stmt.setByte(parameterIndex, s);
			}
		}
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.datatype.DbDataType#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (!super.equals(obj)){
			return false;
		}
		if (!(obj instanceof BitType)){
			return false;
		}
		return true;
	}
}
