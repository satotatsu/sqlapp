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

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.sqlapp.data.converter.Converter;
import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.converter.DefaultConverter;

public class DefaultJdbcTypeHandler implements Serializable, JdbcTypeHandler {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 7620601581332242273L;
	/**
	 * 取得するオブジェクトのjava.sql.Types
	 */
	protected java.sql.JDBCType jdbcType = java.sql.JDBCType.VARCHAR;

	@SuppressWarnings("rawtypes")
	protected Converter statementConverter = new DefaultConverter();
	@SuppressWarnings("rawtypes")
	protected Converter resultSetconverter = new DefaultConverter();

	/**
	 * コンストラクタ
	 * 
	 * @param types
	 *            Typesオブジェクト
	 */
	public DefaultJdbcTypeHandler(DataType types) {
		jdbcType = types.getJdbcType();
		if (types.getDefaultClass() != null) {
			Converter<?> converter = Converters.getDefault().getConverter(
					types.getDefaultClass());
			this.statementConverter = converter;
			this.resultSetconverter = converter;
		}
	}

	/**
	 * コンストラクタ
	 * 
	 * @param jdbcType
	 *            jdbc上の型
	 */
	public DefaultJdbcTypeHandler(java.sql.JDBCType jdbcType) {
		this.jdbcType = jdbcType;
	}

	/**
	 * コンストラクタ
	 * 
	 * @param jdbcType
	 *            jdbc上の型
	 * @param converter
	 *            PrepareedStatementとResultSet用のコンバータ
	 */
	public DefaultJdbcTypeHandler(java.sql.JDBCType jdbcType, Converter<?> converter) {
		this.jdbcType = jdbcType;
		this.statementConverter = converter;
		this.resultSetconverter = converter;
	}

	/**
	 * コンストラクタ
	 * 
	 * @param jdbcType
	 *            jdbc上の型
	 * @param statementConverter
	 *            PrepareedStatement用のコンバータ
	 * @param resultSetconverter
	 *            ResultSet用のコンバータ
	 */
	public DefaultJdbcTypeHandler(java.sql.JDBCType jdbcType,
			Converter<?> statementConverter, Converter<?> resultSetconverter) {
		this.jdbcType = jdbcType;
		this.statementConverter = statementConverter;
		this.resultSetconverter = resultSetconverter;
	}

	/**
	 * ResultSetからの値の取得
	 * 
	 * @param rs
	 * @param columnIndex
	 * @throws SQLException
	 */
	@Override
	public Object getObject(ResultSet rs, int columnIndex) throws SQLException {
		return resultSetconverter.convertObject(rs.getObject(columnIndex));
	}

	/**
	 * ResultSetからの値の取得
	 * 
	 * @param rs
	 * @param columnLabel
	 * @throws SQLException
	 */
	@Override
	public Object getObject(ResultSet rs, String columnLabel)
			throws SQLException {
		return resultSetconverter.convertObject(rs.getObject(columnLabel));
	}

	/**
	 * PreparedStatementへの値の設定
	 * 
	 * @param stmt
	 * @param parameterIndex
	 * @param x
	 * @throws SQLException
	 */
	@Override
	public void setObject(PreparedStatement stmt, int parameterIndex, Object x)
			throws SQLException {
		if (x == null) {
			stmt.setNull(parameterIndex, jdbcType.getVendorTypeNumber());
		} else {
			stmt.setObject(parameterIndex,
					statementConverter.convertObject(x, stmt.getConnection()),
					jdbcType);
		}
	}

	/**
	 * @param statementConverter
	 *            the statementConverter to set
	 */
	public DefaultJdbcTypeHandler setStatementConverter(
			Converter<?> statementConverter) {
		this.statementConverter = statementConverter;
		return this;
	}

	/**
	 * @param resultSetconverter
	 *            the resultSetconverter to set
	 */
	public DefaultJdbcTypeHandler setResultSetconverter(
			Converter<?> resultSetconverter) {
		this.resultSetconverter = resultSetconverter;
		return this;
	}

	/**
	 * @param jdbcType
	 *            the jdbcType to set
	 */
	public DefaultJdbcTypeHandler setJdbcType(java.sql.JDBCType jdbcType) {
		this.jdbcType = jdbcType;
		return this;
	}

}
