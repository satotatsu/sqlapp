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
public final class PostgresIntervalJdbcTypeHandler extends DefaultJdbcTypeHandler
		implements com.sqlapp.data.db.datatype.IntervalTypeHandler {
	private static final long serialVersionUID = 1L;

	@Override public boolean fixedArrayDimensions() { return false; }
	@Override public com.sqlapp.data.db.datatype.JdbcReadScope prepareRead(PreparedStatement statement) throws SQLException {
		if (statement.getConnection().getMetaData().getDatabaseMajorVersion() >= 15)
			return com.sqlapp.data.db.datatype.JdbcReadScope.NONE;
		return PostgresIntervalBinary.prepareRead(statement);
	}

	@Override
	public com.sqlapp.data.db.datatype.IntervalConversion prepareInterval(Object value, Column destination) {
		if (destination.getArrayDimension() > 0) {
			int dimensions = com.sqlapp.data.db.datatype.IntervalArrays.dimensions(value);
			com.sqlapp.data.db.datatype.IntervalArrays.validateBounds(value, dimensions,
					destination.getSpecifics().get(com.sqlapp.data.db.datatype.IntervalArrays.DECLARED_BOUNDS));
			if (dimensions < 1 || dimensions > 6) throw new IllegalArgumentException("PostgreSQL arrays require one to six actual dimensions");
			com.sqlapp.data.db.datatype.IntervalArrays.rectangular(value, dimensions);
			var scalar = destination.clone().setArrayDimension(0);
			return new com.sqlapp.data.db.datatype.IntervalConversion(
					com.sqlapp.data.db.datatype.IntervalArrays.map(value, dimensions,
							leaf -> prepareInterval(leaf, scalar).value()), java.util.List.of());
		}
		var interval = PostgresIntervalCodec.decode(value);
		PostgresIntervalCodec.encode(interval, destination);
		return com.sqlapp.data.db.datatype.IntervalConversion.unchanged(interval);
	}

	@Override public boolean requiresJdbcBinding(Object value, Column destination, java.sql.DatabaseMetaData server)
			throws SQLException {
		return server.getDatabaseMajorVersion() < 15 && PostgresIntervalBinary.required(value);
	}

	@Override
	public com.sqlapp.data.db.datatype.IntervalPrecision intervalPrecision(Column column) {
		return new com.sqlapp.data.db.datatype.IntervalPrecision(-1,
				column.getDataType() == DataType.INTERVAL || column.getDataType().name().endsWith("SECOND")
						? column.getLength() == null ? 6 : Math.toIntExact(column.getLength()) : 0);
	}

	@Override
	public Column adaptIntervalColumn(Column destination, com.sqlapp.data.db.datatype.IntervalPrecision precision) {
		return destination.setLength(Math.min(precision.fractional(), 6)).setScale(null);
	}

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
			if (value == null) { statement.setNull(index, java.sql.Types.ARRAY); return; }
			prepareInterval(value, column);
			if (PostgresIntervalBinary.required(value)) {
				PostgresIntervalBinary.bind(statement, index, value, true); return;
			}
			var scalar = column.clone().setArrayDimension(0);
			var text = (Object[]) com.sqlapp.data.db.datatype.IntervalArrays.map(value,
					com.sqlapp.data.db.datatype.IntervalArrays.dimensions(value),
					leaf -> PostgresIntervalCodec.encode(PostgresIntervalCodec.decode(leaf), scalar));
			var array = statement.getConnection().createArrayOf("interval", text);
			try { statement.setArray(index, array); } finally { array.free(); }
			return;
		}
		final IntervalValue interval = PostgresIntervalCodec.decode(value);
		if (interval != null) {
			PostgresIntervalCodec.encode(interval, column);
		}
		setObject(statement, index, interval);
	}

	@Override public void setObject(PreparedStatement statement, int index, Object value) throws SQLException {
		if (!(statementConverter instanceof ToPGIntervalConverter)) { super.setObject(statement, index, value); return; }
		var converted = (org.postgresql.util.PGobject) statementConverter.convertObject(value);
		var interval = PostgresIntervalCodec.decode(converted);
		if (interval != null && PostgresIntervalBinary.required(interval)) PostgresIntervalBinary.bind(statement, index, interval, false);
		else statement.setObject(index, converted, java.sql.Types.OTHER);
	}

	@Override
	public Object getObject(final ResultSet result, final int column) throws SQLException {
		if (PostgresIntervalBinary.binary(result, column)) {
			byte[] bytes = result.getBytes(column);
			return bytes == null ? null : com.sqlapp.data.db.datatype.IntervalArrays.array(result, column)
					? PostgresIntervalBinary.readArray(bytes) : PostgresIntervalBinary.readScalar(bytes);
		}
		if (com.sqlapp.data.db.datatype.IntervalArrays.array(result, column))
			return readArray(result.getArray(column), result.getString(column));
		return resultSetconverter.convertObject(result.getString(column));
	}

	@Override
	public Object getObject(final ResultSet result, final String column) throws SQLException {
		if (result.getMetaData() instanceof org.postgresql.jdbc.PgResultSetMetaData)
			return getObject(result, result.findColumn(column));
		if (result.getMetaData() != null && com.sqlapp.data.db.datatype.IntervalArrays.array(result, result.findColumn(column)))
			return readArray(result.getArray(column), result.getString(column));
		return resultSetconverter.convertObject(result.getString(column));
	}

	private Object readArray(java.sql.Array array, String text) throws SQLException {
		if (text != null && text.startsWith("[")) {
			if (array != null) array.free();
			throw new SQLException("Interval arrays with non-default lower bounds cannot be represented by Java arrays");
		}
		return com.sqlapp.data.db.datatype.IntervalArrays.read(array, PostgresIntervalCodec::parse);
	}
}
