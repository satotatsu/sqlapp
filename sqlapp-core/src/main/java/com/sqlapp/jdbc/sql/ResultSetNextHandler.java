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
package com.sqlapp.jdbc.sql;

import java.sql.ResultSet;
import java.sql.SQLException;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.jdbc.ExResultSet;

public abstract class ResultSetNextHandler {

	private Converters converters=Converters.getDefault();
	
	/**
	 * 
	 * @param rs
	 * @throws SQLException
	 */
	public abstract void handleResultSetNext(ExResultSet rs) throws SQLException;

	/**
	 * ResultSetから指定したカラムの文字列値を取得します。
	 * @param rs
	 * @param columnName
	 * @throws SQLException
	 */
	public String getString(ExResultSet rs, String columnName) throws SQLException{
		return rs.getString(columnName);
	}

	/**
	 * ResultSetから指定したカラムのInteger値を取得します。
	 * @param rs
	 * @param columnName
	 * @throws SQLException
	 */
	public Integer getInteger(ResultSet rs, String columnName) throws SQLException{
		Object obj= rs.getObject(columnName);
		return getConverters().convertObject(obj, Integer.class);
	}
	
	/**
	 * @return the converters
	 */
	protected Converters getConverters() {
		return converters;
	}

	protected boolean useNString(){
		return false;
	}
	
}
