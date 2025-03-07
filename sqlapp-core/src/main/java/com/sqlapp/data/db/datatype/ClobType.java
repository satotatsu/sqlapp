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

import java.io.Reader;
import java.sql.Clob;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.sqlapp.data.converter.Converter;
import com.sqlapp.data.converter.StringConverter;

import static com.sqlapp.util.CommonUtils.*;

/**
 * CLOBを表す型
 * @author satoh
 *
 */
public class ClobType extends AbstractLengthType<ClobType>{

	/** serialVersionUID */
	private static final long serialVersionUID = -8658816953027318522L;
	/**
	 * コンバータ
	 */
	protected StringConverter converter=new StringConverter();
	/**
	 * コンストラクタ
	 */
	public ClobType(){
		this(DataType.CLOB.getTypeName());
	}

	protected ClobType(String dataTypeName){
		this.setDataType(DataType.CLOB);
		initialize(dataTypeName);
		this.setJdbcTypeHandler(new CLOBTypeHandler(this.getDataType().getJdbcType(), converter));
		setCaseSensitive(true);
		setDefaultLength(LEN_2GB);
		setLiteralPrefix("'");
		setLiteralSuffix("'");
		setDefaultValueLiteral(withLiteral(""));
	}

	/**
	 * CLOBをJDBCで扱うためのデフォルトのハンドラー
	 * @author satoh
	 *
	 */
	private static class CLOBTypeHandler extends DefaultJdbcTypeHandler{
		public CLOBTypeHandler(java.sql.JDBCType jdbcType, Converter<?> converter) {
			super(jdbcType, converter);
		}
		/** serialVersionUID */
		private static final long serialVersionUID = -3446371652551511555L;
		@Override
		public void setObject(PreparedStatement stmt, int parameterIndex
				, Object x) throws SQLException{
			if (x==null){
				stmt.setNull(parameterIndex, java.sql.Types.CLOB);
				return;
			}
			if (x instanceof Clob){
				stmt.setClob(parameterIndex, (Clob)x);
				return;
			}
			if (x instanceof Reader){
				stmt.setClob(parameterIndex, (Reader)x);
				return;
			}
			String val=cast(this.statementConverter.convertObject(x));
			stmt.setObject(parameterIndex, val, java.sql.Types.CLOB);
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
		if (!(obj instanceof ClobType)){
			return false;
		}
		return true;
	}
}
