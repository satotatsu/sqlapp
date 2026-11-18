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
import java.sql.ResultSet;
import java.sql.SQLException;

import com.sqlapp.data.schemas.Column;

public interface JdbcTypeHandler {
	/** Configure a newly created, SQLapp-owned read statement; close restores temporary connection settings. */
	default JdbcReadScope prepareRead(PreparedStatement statement) throws SQLException { return JdbcReadScope.NONE; }
	/** Exact server-side projection for typed JDBC readers. The argument is already identifier-quoted. */
	default String readExpression(String quotedColumn, Column column) { return quotedColumn; }

	/**
	 * ResultSetから値を取得します
	 * 
	 * @param rs
	 * @param columnIndex
	 * @throws SQLException
	 */
	Object getObject(ResultSet rs, int columnIndex) throws SQLException;

	/**
	 * ResultSetから値を取得します
	 * 
	 * @param rs
	 * @param columnLabel
	 * @throws SQLException
	 */
	Object getObject(ResultSet rs, String columnLabel) throws SQLException;

	/** Read with canonical Schema qualifiers when a driver exposes only a broad JDBC type. */
	default Object getObject(ResultSet rs, int index, Column column) throws SQLException {
		return getObject(rs, index);
	}

	/**
	 * PreparedStatementへ値を設定します
	 * 
	 * @param stmt
	 * @param parameterIndex
	 * @param x
	 * @throws SQLException
	 */
	void setObject(PreparedStatement stmt, int parameterIndex, Object x)
			throws SQLException;

	/** Column-aware binding for precision/qualifier checks; existing handlers retain their behavior. */
	default void setObject(PreparedStatement stmt, int parameterIndex, Object value,
			Column column) throws SQLException {
		setObject(stmt, parameterIndex, value);
	}
}
