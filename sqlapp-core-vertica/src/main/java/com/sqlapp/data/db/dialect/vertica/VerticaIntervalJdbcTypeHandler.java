/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.vertica;

import java.math.BigInteger;
import java.sql.*;
import java.time.Duration;
import com.sqlapp.data.converter.IntervalValueConverter;
import com.sqlapp.data.db.datatype.*;
import com.sqlapp.data.interval.IntervalValue;
import com.sqlapp.data.schemas.Column;
import com.vertica.jdbc.VerticaDayTimeInterval;
import com.vertica.jdbc.VerticaYearMonthInterval;

/** Exact native JDBC interval objects; no 30-day month conversion or double fractions. */
public final class VerticaIntervalJdbcTypeHandler implements IntervalTypeHandler, java.io.Serializable {
	private static final long serialVersionUID = 1L;
	private final DataType type;
	public VerticaIntervalJdbcTypeHandler(DataType type) { this.type = type; }
	private boolean yearMonth() { return type.name().contains("YEAR") || type == DataType.INTERVAL_MONTH; }
	private StandardIntervalCodec codec(Column column) {
		if (column.getArrayDimension() > 0) throw new IllegalArgumentException("Vertica interval array binding is not qualified");
		int fraction = type == DataType.INTERVAL || type.name().endsWith("SECOND")
				? column.getLength() == null ? 6 : Math.toIntExact(column.getLength()) : 0;
		if (fraction < 0 || fraction > 6) throw new IllegalArgumentException("Vertica interval precision must be 0..6");
		return new StandardIntervalCodec(type == DataType.INTERVAL ? DataType.INTERVAL_DAY_TO_SECOND : type, 18, fraction);
	}
	public IntervalValue decode(Object value) {
		if (value instanceof VerticaYearMonthInterval month) {
			long total = Math.addExact(Math.multiplyExact((long) month.getYear(), 12), month.getMonth());
			return new IntervalValue(month.isNegative() ? -total : total, 0, Duration.ZERO);
		}
		if (value instanceof VerticaDayTimeInterval time) {
			int precision = time.getPrecision();
			if (precision < 0 || precision > 6) throw new IllegalArgumentException("Unexpected Vertica fractional precision");
			long nanos = Math.multiplyExact((long) time.getFraction(), BigInteger.TEN.pow(9 - precision).longValueExact());
			var duration = Duration.ofDays(time.getDay()).plusHours(time.getHour()).plusMinutes(time.getMinute())
					.plusSeconds(time.getSecond()).plusNanos(nanos);
			return new IntervalValue(0, 0, time.isNegative() ? duration.negated() : duration);
		}
		if (value instanceof String text && !text.startsWith("P") && !text.startsWith("p"))
			return new StandardIntervalCodec(yearMonth() ? DataType.INTERVAL_YEAR_TO_MONTH : DataType.INTERVAL_DAY_TO_SECOND, 18, 6).parse(text);
		return new IntervalValueConverter().convertObject(value);
	}
	@Override public void validateIntervalColumn(Column column) { codec(column); }
	@Override public IntervalConversion prepareInterval(Object input, Column column) {
		var result = codec(column).convert(decode(input));
		if (yearMonth()) {
			// The public driver constructor has int year/month fields, unlike the server's int64 month storage.
			BigInteger.valueOf(result.value().months()).abs().divide(BigInteger.valueOf(12)).intValueExact();
		} else {
			var time = result.value().timePart();
			BigInteger.valueOf(time.getSeconds()).multiply(BigInteger.valueOf(1_000_000))
					.add(BigInteger.valueOf(time.getNano() / 1000)).longValueExact();
		}
		return new IntervalConversion(result.value(), result.warnings().stream().map(Enum::name).toList());
	}
	public Object nativeValue(Object input, Column column) {
		var value = (IntervalValue) prepareInterval(input, column).value();
		if (yearMonth()) {
			var magnitude = BigInteger.valueOf(value.months()).abs().divideAndRemainder(BigInteger.valueOf(12));
			return new VerticaYearMonthInterval(magnitude[0].intValueExact(), magnitude[1].intValueExact(), value.months() < 0);
		}
		var time = value.timePart().abs();
		long seconds = time.getSeconds();
		return new VerticaDayTimeInterval(Math.toIntExact(seconds / 86400), (int) (seconds / 3600 % 24),
				(int) (seconds / 60 % 60), (int) (seconds % 60), time.getNano() / 1000, 6, value.timePart().isNegative());
	}
	@Override public IntervalPrecision intervalPrecision(Column column) {
		return new IntervalPrecision(-1, type == DataType.INTERVAL || type.name().endsWith("SECOND")
				? column.getLength() == null ? 6 : Math.toIntExact(column.getLength()) : 0);
	}
	@Override public Column adaptIntervalColumn(Column column, IntervalPrecision precision) {
		return column.setLength(Math.min(precision.fractional(), 6)).setScale(null).setDataTypeName(null);
	}
	@Override public Object getObject(ResultSet rows, int index) throws SQLException {
		Object value = rows.getObject(index); return value == null ? null : decode(value);
	}
	@Override public Object getObject(ResultSet rows, String name) throws SQLException {
		Object value = rows.getObject(name); return value == null ? null : decode(value);
	}
	@Override public void setObject(PreparedStatement statement, int index, Object value) throws SQLException {
		setObject(statement, index, value, new Column().setDataType(type));
	}
	@Override public void setObject(PreparedStatement statement, int index, Object value, Column column) throws SQLException {
		codec(column);
		if (value == null) statement.setNull(index, Types.OTHER);
		else statement.setObject(index, nativeValue(value, column));
	}
	public String literal(Object value, Column column) {
		var projected = prepareInterval(value, column).value();
		String qualifier = type == DataType.INTERVAL ? "DAY TO SECOND" : type.name().substring(9).replace('_', ' ');
		return "INTERVAL '" + codec(column).convert(projected).text() + "' " + qualifier
				+ (qualifier.endsWith("SECOND") ? "(" + intervalPrecision(column).fractional() + ")" : "");
	}
}
