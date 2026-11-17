/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.duckdb;

import java.sql.*;
import java.io.Serializable;
import com.sqlapp.data.db.datatype.*;
import com.sqlapp.data.schemas.Column;

public class DuckDbIntervalJdbcTypeHandler implements IntervalTypeHandler, Serializable {
	private static final long serialVersionUID = 1L;
	@Override public IntervalConversion prepareInterval(Object value, Column column) {
		if (column.getArrayDimension() > 0) {
			IntervalArrays.validateBounds(value, column.getArrayDimension(),
					column.getSpecifics().get(IntervalArrays.DECLARED_BOUNDS));
			return new IntervalConversion(IntervalArrays.map(value, column.getArrayDimension(),
					leaf -> prepareInterval(leaf, column.clone().setArrayDimension(0)).value()), java.util.List.of());
		}
		var interval = DuckDbIntervalCodec.decode(value);
		DuckDbIntervalCodec.encode(interval);
		return IntervalConversion.unchanged(interval);
	}
	@Override public IntervalPrecision intervalPrecision(Column column) { return new IntervalPrecision(-1, 6); }
	@Override public Column adaptIntervalColumn(Column column, IntervalPrecision precision) { return column.setLength(null).setScale(null); }
	@Override public Object getObject(ResultSet rows, int index) throws SQLException {
		if (IntervalArrays.array(rows, index)) return read(rows.getArray(index));
		String text = rows.getString(index);
		return text == null ? null : DuckDbIntervalCodec.decode(text);
	}
	@Override public Object getObject(ResultSet rows, String name) throws SQLException { return getObject(rows, rows.findColumn(name)); }
	private Object read(java.sql.Array array) throws SQLException {
		if (array == null) return null;
		try { return mapRead(array.getArray()); } finally { array.free(); }
	}
	private Object mapRead(Object value) throws SQLException {
		if (value == null) return null;
		if (value instanceof java.sql.Array array) return read(array);
		if (value.getClass().isArray()) {
			var out = new Object[java.lang.reflect.Array.getLength(value)];
			for (int i = 0; i < out.length; i++) out[i] = mapRead(java.lang.reflect.Array.get(value, i));
			return out;
		}
		return DuckDbIntervalCodec.decode(value);
	}
	@Override public void setObject(PreparedStatement statement, int index, Object value) throws SQLException {
		if (value == null) statement.setNull(index, Types.OTHER);
		else statement.setString(index, DuckDbIntervalCodec.encode(value));
	}
	@Override public void setObject(PreparedStatement statement, int index, Object value, Column column) throws SQLException {
		if (column.getArrayDimension() == 0 || value == null) { setObject(statement, index, value); return; }
		prepareInterval(value, column);
		var text = IntervalArrays.map(value, column.getArrayDimension(), DuckDbIntervalCodec::encode);
		statement.setString(index, new com.sqlapp.util.JsonConverter().toJsonString(text));
	}
	public String literal(Object value, Column column) {
		prepareInterval(value, column);
		if (column.getArrayDimension() == 0) return "INTERVAL '" + DuckDbIntervalCodec.encode(value) + "'";
		return "CAST(" + IntervalArrays.literal(value, column.getArrayDimension(),
				leaf -> "INTERVAL '" + DuckDbIntervalCodec.encode(leaf) + "'")
				+ " AS INTERVAL" + "[]".repeat(column.getArrayDimension()) + ")";
	}
}
