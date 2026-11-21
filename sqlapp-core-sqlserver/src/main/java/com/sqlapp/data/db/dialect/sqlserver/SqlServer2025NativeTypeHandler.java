/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

import com.sqlapp.data.db.datatype.JdbcTypeHandler;

import microsoft.sql.Vector;

/** Keep the portable schema row values as text. */
final class SqlServer2025NativeTypeHandler implements JdbcTypeHandler {
	private final boolean vector;

	SqlServer2025NativeTypeHandler(boolean vector) {
		this.vector = vector;
	}

	@Override
	public Object getObject(ResultSet rs, int index) throws SQLException {
		if (!vector)
			return rs.getString(index);
		Object value = rs.getObject(index);
		return vectorText(value instanceof byte[] ? rs.getObject(index, Vector.class) : value);
	}

	@Override
	public Object getObject(ResultSet rs, String label) throws SQLException {
		return vector ? getObject(rs, rs.findColumn(label)) : rs.getString(label);
	}

	private String vectorText(Object value) throws SQLException {
		if (value == null || value instanceof String)
			return (String) value;
		if (value instanceof Vector nativeValue)
			return nativeValue.getData() == null ? null : Arrays.toString(nativeValue.getData());
		throw new SQLException("Unexpected SQL Server VECTOR representation: " + value.getClass().getName());
	}

	@Override
	public void setObject(PreparedStatement stmt, int index, Object value) throws SQLException {
		if (vector && value instanceof Vector nativeValue)
			stmt.setObject(index, nativeValue, microsoft.sql.Types.VECTOR);
		else if (vector)
			stmt.setString(index, value == null ? null : value.toString());
		else
			stmt.setObject(index, value == null ? null : value.toString(), microsoft.sql.Types.JSON);
	}
}
