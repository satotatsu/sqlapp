/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.sql;

import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.DbDataType;
import com.sqlapp.data.db.dialect.Dialect;

/** Shared value binding for SQL operations and portable bulk writes. */
public final class JdbcParameterBinder {
	private JdbcParameterBinder() {
	}

	/**
	 * Binds one value using the dialect's type handler when a type is known.
	 * The index is one-based. Transactions and supplied streams belong to the caller.
	 */
	public static void bind(final PreparedStatement statement, final Dialect dialect,
			final DataType type, final int index, final Object value) throws SQLException {
		if (dialect != null && type != null) {
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
			} else {
				statement.setObject(index, value);
			}
		}
	}
}
