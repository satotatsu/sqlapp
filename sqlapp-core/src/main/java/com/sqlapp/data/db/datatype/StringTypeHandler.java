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

import com.sqlapp.data.converter.Converter;

public class StringTypeHandler extends DefaultJdbcTypeHandler{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -2753153458114456111L;

	/**
	 * コンストラクタ
	 * @param types Typesオブジェクト
	 */
	public StringTypeHandler(DataType types){
		super(types);
	}
	
	public StringTypeHandler(java.sql.JDBCType jdbcType, Converter<?> converter) {
		super(jdbcType, converter);
	}
	@SuppressWarnings("unchecked")
	@Override
	public void setObject(PreparedStatement stmt, int parameterIndex
			, Object x) throws SQLException{
		if (x==null){
			stmt.setNull(parameterIndex, jdbcType.getVendorTypeNumber());
			return;
		}
		String val=this.statementConverter.convertString(x);
		stmt.setObject(parameterIndex, val, jdbcType);
	}
}
