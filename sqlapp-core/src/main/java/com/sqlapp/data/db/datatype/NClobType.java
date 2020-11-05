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

import java.io.Reader;
import java.sql.Clob;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.sqlapp.data.converter.Converter;
import com.sqlapp.data.converter.StringConverter;

import static com.sqlapp.util.CommonUtils.*;

/**
 * NCLOBを表す型
 * @author satoh
 *
 */
public class NClobType extends AbstractLengthType<NClobType>{

	/** serialVersionUID */
	private static final long serialVersionUID = -8658816953027318522L;
	/**
	 * コンバータ
	 */
	protected StringConverter converter=new StringConverter();
	/**
	 * コンストラクタ
	 */
	public NClobType(){
		this(DataType.NCLOB.getTypeName());
	}
	
	protected NClobType(String dataTypeName){
		this.setDataType(DataType.NCLOB);
		this.setJdbcTypeHandler(new CLOBTypeHandler(this.getDataType().getJdbcType(), converter));
		setDefaultLength(LEN_1GB);
		initialize(dataTypeName);
		setLiteralPrefix("N'");
		setLiteralSuffix("'");
		setDefaultValueLiteral(withLiteral(""));
		setCharset("UTF-16");
	}

	/**
	 * NCLOBをJDBCで扱うためのデフォルトのハンドラー
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
				stmt.setNull(parameterIndex, java.sql.Types.NCLOB);
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
			stmt.setObject(parameterIndex, val, java.sql.Types.NCLOB);
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
		if (!(obj instanceof NClobType)){
			return false;
		}
		return true;
	}
}
