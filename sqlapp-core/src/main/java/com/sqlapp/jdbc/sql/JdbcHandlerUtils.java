/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.jdbc.sql;

import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.DbDataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.util.CommonUtils;

public class JdbcHandlerUtils {

	/**
	 * バインド変数の設定
	 * 
	 * @param statement
	 * @param sqlParameters
	 * @throws SQLException
	 */
	public static List<BindParameter> setBind(final PreparedStatement statement, final Dialect dialect,
			final SqlParameterCollection sqlParameters) throws SQLException {
		final List<BindParameter> list = sqlParameters.getBindParameters();
		final int size = list.size();
		for (int i = 0; i < size; i++) {
			final BindParameter bindParameter = list.get(i);
			setParameters(statement, dialect, bindParameter, i + 1);
		}
		return list;
	}

	/**
	 * PreparedStatementに値を設定します
	 * 
	 * @param statement     PreparedStatement
	 * @param dialect       Dialect
	 * @param bindParameter BindParameter
	 * @param index
	 * @throws SQLException
	 */
	public static void setParameters(final PreparedStatement statement, final Dialect dialect,
			final BindParameter bindParameter, final int index) throws SQLException {
		final DataType type = bindParameter.getType();
		final Object value = bindParameter.getValue();
		if (dialect != null && bindParameter.getType() != null) {
			final DbDataType<?> dbDataType = dialect.getDbDataTypes().getDbType(type);
			dbDataType.getJdbcTypeHandler().setObject(statement, index, value);
		} else {
			if (value instanceof String) {
				if (dialect != null && dialect.recommendsNTypeChar()) {
					statement.setNString(index, (String) value);
				} else {
					statement.setString(index, (String) value);
				}
			} else if (value instanceof Number) {
				if (value instanceof Integer) {
					statement.setInt(index, (Integer) value);
				} else if (value instanceof Long) {
					statement.setLong(index, (Long) value);
				} else if (value instanceof BigDecimal) {
					statement.setBigDecimal(index, (BigDecimal) value);
				} else if (value instanceof Byte) {
					statement.setByte(index, (Byte) value);
				} else if (value instanceof Float) {
					statement.setFloat(index, (Float) value);
				} else if (value instanceof Double) {
					statement.setDouble(index, (Double) value);
				} else {
					statement.setBigDecimal(index, Converters.getDefault().convertObject(value, BigDecimal.class));
				}
			} else if (value instanceof Boolean) {
				statement.setBoolean(index, (Boolean) value);
			} else if (value instanceof byte[]) {
				statement.setBytes(index, (byte[]) value);
			} else if (value instanceof Enum) {
				statement.setObject(index, Converters.getDefault().convertString(value));
			} else if (value instanceof java.sql.Time || value instanceof LocalTime) {
				statement.setTime(index, Converters.getDefault().convertObject(value, java.sql.Time.class));
			} else if (value instanceof java.sql.Date || value instanceof LocalDate) {
				statement.setDate(index, Converters.getDefault().convertObject(value, java.sql.Date.class));
			} else if (value instanceof Date || value instanceof LocalDateTime) {
				statement.setTimestamp(index, Converters.getDefault().convertObject(value, Timestamp.class));
			} else if (value instanceof InputStream) {
				statement.setBinaryStream(index, (InputStream) value);
			} else if (value instanceof URL) {
				statement.setURL(index, Converters.getDefault().convertObject(value, URL.class));
			} else if (value instanceof URI) {
			} else {
				statement.setObject(index, value);
			}
		}
	}

	/**
	 * 生成されたキーを扱います
	 * 
	 * @param statement           PreparedStatement
	 * @param generatedKeyHandler GeneratedKeyHandler
	 * @throws SQLException
	 */
	public static void handleGeneratedKeys(final PreparedStatement statement, GeneratedKeyHandler generatedKeyHandler,
			Dialect dialect) throws SQLException {
		if (generatedKeyHandler == null) {
			return;
		}
		try (final ResultSet rs = statement.getGeneratedKeys()) {
			final ResultSetMetaData metaData = rs.getMetaData();
			long rowNo = 0;
			while (rs.next()) {
				for (int i = 1; i <= metaData.getColumnCount(); i++) {
					generatedKeyHandler.handle(rowNo, new GeneratedKeyInfo(metaData, rs, i));
				}
				rowNo++;
			}
		}
	}

	/**
	 * 生成されたキーを取得します *
	 * 
	 * @param statement           PreparedStatement
	 * @param generatedKeyHandler GeneratedKeyHandler
	 * @return 生成されたキーのリスト
	 * @throws SQLException
	 */
	public static List<GeneratedKeyInfo> getGeneratedKeys(final PreparedStatement statement, Dialect dialect)
			throws SQLException {
		try (final ResultSet rs = statement.getGeneratedKeys()) {
			if (rs.isClosed()) {
				return Collections.emptyList();
			}
			final ResultSetMetaData metaData = rs.getMetaData();
			final List<GeneratedKeyInfo> result = CommonUtils.list();
			while (rs.next()) {
				for (int i = 1; i <= metaData.getColumnCount(); i++) {
					result.add(new GeneratedKeyInfo(metaData, rs, i));
				}
			}
			return result;
		}
	}

	/**
	 * PreparedStatementを生成します
	 * 
	 * @param connection    Connection
	 * @param sqlParameters SqlParameterCollection
	 * @return PreparedStatement
	 * @throws SQLException
	 */
	public static PreparedStatement getStatement(final Connection connection,
			final SqlParameterCollection sqlParameters) throws SQLException {
		PreparedStatement statement = null;
		if (sqlParameters.getGeneratedKey() != null) {
			statement = connection.prepareStatement(sqlParameters.getSql(), sqlParameters.getGeneratedKey().getValue());
		} else {
			if (sqlParameters.getResultSetType() != null || sqlParameters.getResultSetHoldability() != null
					|| sqlParameters.getResultSetConcurrency() != null) {
				statement = getStatementForQuery(connection, sqlParameters.getSql(), sqlParameters.getResultSetType(),
						sqlParameters.getResultSetConcurrency(), sqlParameters.getResultSetHoldability());
			} else {
				statement = connection.prepareStatement(sqlParameters.getSql());
			}
		}
		return statement;
	}

	/**
	 * PreparedStatementを生成します
	 * 
	 * @param connection           connection
	 * @param sql                  SQL
	 * @param resultSetType        ResultSetType
	 * @param resultSetConcurrency ResultSetConcurrency
	 * @return resultSetHoldability ResultSetHoldability
	 * @return PreparedStatement
	 * @throws SQLException
	 */
	public static PreparedStatement getStatementForQuery(final Connection connection, String sql,
			ResultSetType resultSetType, ResultSetConcurrency resultSetConcurrency,
			ResultSetHoldability resultSetHoldability) throws SQLException {
		final PreparedStatement statement = connection.prepareStatement(sql,
				resultSetType != null ? resultSetType.getValue() : ResultSetType.getDefault().getValue(),
				resultSetConcurrency != null ? resultSetConcurrency.getValue()
						: ResultSetConcurrency.getDefault().getValue(),
				resultSetHoldability != null ? resultSetHoldability.getValue()
						: ResultSetHoldability.getDefault().getValue());
		return statement;
	}

}
