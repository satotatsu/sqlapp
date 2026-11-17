/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.drsum;

import java.sql.*;
import com.sqlapp.data.db.datatype.*;
import com.sqlapp.data.schemas.Column;

/** Experimental Dr.Sum text adapter; length is fractional precision, not a leading-day precision. */
public final class DrSumIntervalJdbcTypeHandler implements IntervalTypeHandler, java.io.Serializable {
	private static final long serialVersionUID = 1L;
	private StandardIntervalCodec codec(Column column) {
		if (column.getArrayDimension() > 0) throw new IllegalArgumentException("Dr.Sum does not support interval array columns");
		int fraction = column.getLength() == null ? 0 : Math.toIntExact(column.getLength());
		if (fraction < 0 || fraction > 6) throw new IllegalArgumentException("Dr.Sum interval precision must be 0..6");
		return new StandardIntervalCodec(DataType.INTERVAL_DAY_TO_SECOND, 18, fraction);
	}
	@Override public IntervalConversion prepareInterval(Object value, Column column) {
		var result = codec(column).convert(value);
		return new IntervalConversion(result.value(), result.warnings().stream().map(Enum::name).toList());
	}
	@Override public IntervalPrecision intervalPrecision(Column column) {
		codec(column);
		return new IntervalPrecision(-1, column.getLength() == null ? 0 : Math.toIntExact(column.getLength()));
	}
	@Override public Column adaptIntervalColumn(Column column, IntervalPrecision precision) {
		return column.setLength(Math.min(precision.fractional(), 6)).setScale(null).setDataTypeName(null);
	}
	@Override public Object getObject(ResultSet rows, int index) throws SQLException {
		String text = rows.getString(index);
		return text == null ? null : new StandardIntervalCodec(DataType.INTERVAL_DAY_TO_SECOND, 18, 6).parse(text);
	}
	@Override public Object getObject(ResultSet rows, String name) throws SQLException {
		String text = rows.getString(name);
		return text == null ? null : new StandardIntervalCodec(DataType.INTERVAL_DAY_TO_SECOND, 18, 6).parse(text);
	}
	@Override public void setObject(PreparedStatement statement, int index, Object value) throws SQLException {
		setObject(statement, index, value, new Column().setDataType(DataType.INTERVAL_DAY_TO_SECOND));
	}
	@Override public void setObject(PreparedStatement statement, int index, Object value, Column column) throws SQLException {
		var codec = codec(column);
		if (value == null) statement.setNull(index, Types.OTHER);
		else statement.setString(index, codec.convert(value).text());
	}
	public String literal(Object value, Column column) {
		var codec = codec(column);
		return value == null ? "NULL" : "INTERVAL '" + codec.convert(value).text() + "'";
	}
}
