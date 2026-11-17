/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres;

import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.DefaultJdbcTypeHandler;
import com.sqlapp.data.db.dialect.postgres.converter.FromPGIntervalConverter;
import com.sqlapp.data.db.dialect.postgres.converter.ToPGIntervalConverter;
import com.sqlapp.data.interval.IntervalValue;
import com.sqlapp.data.schemas.Column;

/** Reads server text to avoid PGInterval's int-hour limit on valid large values. */
public final class PostgresIntervalJdbcTypeHandler extends DefaultJdbcTypeHandler {
	private static final long serialVersionUID = 1L;

	public PostgresIntervalJdbcTypeHandler() {
		this(DataType.INTERVAL, 6);
	}

	public PostgresIntervalJdbcTypeHandler(final DataType type) {
		this(type, 6);
	}

	/** Use this constructor when a lower column precision is known. */
	public PostgresIntervalJdbcTypeHandler(final Column column) {
		this(column.getDataType(), column.getLength() == null ? 6 : Math.toIntExact(column.getLength()));
	}

	private PostgresIntervalJdbcTypeHandler(final DataType type, final int precision) {
		super(JDBCType.OTHER, new ToPGIntervalConverter(type, precision), new FromPGIntervalConverter());
	}

	@Override
	public void setObject(final PreparedStatement statement, final int index, final Object value,
			final Column column) throws SQLException {
		if (column.getArrayDimension() > 0) {
			throw new SQLFeatureNotSupportedException("Interval array JDBC binding is not supported; use COPY or bind scalar intervals");
		}
		final IntervalValue interval = PostgresIntervalCodec.decode(value);
		if (interval != null) {
			PostgresIntervalCodec.encode(interval, column);
		}
		super.setObject(statement, index, interval);
	}

	@Override
	public Object getObject(final ResultSet result, final int column) throws SQLException {
		return resultSetconverter.convertObject(result.getString(column));
	}

	@Override
	public Object getObject(final ResultSet result, final String column) throws SQLException {
		return resultSetconverter.convertObject(result.getString(column));
	}
}
