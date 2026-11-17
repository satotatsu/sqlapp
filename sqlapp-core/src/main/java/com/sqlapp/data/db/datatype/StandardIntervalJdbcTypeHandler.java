/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.datatype;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;

import com.sqlapp.data.schemas.Column;

/** Exact text-based JDBC interval handling; vendor subclasses own binding strategy and limits. */
public class StandardIntervalJdbcTypeHandler implements JdbcTypeHandler, java.io.Serializable {
	private static final long serialVersionUID = 1L;
	private final DataType type;
	private final int minimumPrecision;
	private final int maximumPrecision;

	public StandardIntervalJdbcTypeHandler(DataType type, int minimumPrecision, int maximumPrecision) {
		this.type = type;
		this.minimumPrecision = minimumPrecision;
		this.maximumPrecision = maximumPrecision;
		codec(null);
	}

	public final StandardIntervalCodec codec(Column column) {
		if (column != null && (column.getArrayDimension() > 0 || column.getDataType() != type))
			throw new IllegalArgumentException("Expected scalar " + type + " column");
		int precision = column == null || column.getLength() == null ? 2 : Math.toIntExact(column.getLength());
		int scale = column == null || column.getScale() == null ? 6 : column.getScale();
		if (precision < minimumPrecision || precision > maximumPrecision)
			throw new IllegalArgumentException("Leading interval precision must be " + minimumPrecision + ".." + maximumPrecision);
		return new StandardIntervalCodec(type, precision, scale);
	}

	/** Callers preparing migrations can retain warnings before execution. */
	public final StandardIntervalCodec.Result convert(Object value, Column column) {
		return codec(column).convert(value);
	}

	@Override
	public Object getObject(ResultSet rows, int index) throws SQLException {
		String text = rows.getString(index);
		return text == null ? null : codec(null).parse(text);
	}

	@Override
	public Object getObject(ResultSet rows, String label) throws SQLException {
		String text = rows.getString(label);
		return text == null ? null : codec(null).parse(text);
	}

	@Override
	public void setObject(PreparedStatement statement, int index, Object value) throws SQLException {
		setObject(statement, index, value, null);
	}

	@Override
	public void setObject(PreparedStatement statement, int index, Object value, Column column) throws SQLException {
		if (column != null && column.getArrayDimension() > 0)
			throw new SQLFeatureNotSupportedException("Standard interval array binding is not supported");
		StandardIntervalCodec destination = codec(column);
		if (value == null) bindNull(statement, index);
		else bindText(statement, index, destination.convert(value).text());
	}

	protected void bindText(PreparedStatement statement, int index, String text) throws SQLException {
		statement.setString(index, text);
	}

	protected void bindNull(PreparedStatement statement, int index) throws SQLException {
		statement.setNull(index, Types.OTHER);
	}
}
