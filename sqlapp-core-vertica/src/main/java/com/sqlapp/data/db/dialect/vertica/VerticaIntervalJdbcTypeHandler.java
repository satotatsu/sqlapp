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
	private static final com.sqlapp.util.JsonConverter ARRAY_JSON = new com.sqlapp.util.JsonConverter();
	private final DataType type;
	public VerticaIntervalJdbcTypeHandler(DataType type) { this.type = type; }
	private boolean yearMonth() { return type.name().contains("YEAR") || type == DataType.INTERVAL_MONTH; }
	private StandardIntervalCodec codec(Column column) {
		if (column.getArrayDimension() > 0) return codec(column.clone().setArrayDimension(0));
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
	@Override public void validateIntervalColumn(Column column) {
		if (column.getArrayDimension() < 0 || column.getArrayDimension() > 32) throw new IllegalArgumentException("Vertica interval arrays require 0..32 dimensions");
		if(yearMonth() && column.getArrayDimension()>1)throw new IllegalArgumentException("Nested Vertica YEAR/MONTH intervals are not supported by the server JSON parser or JDBC parameter casts");
		codec(column);
	}
	private IntervalValue parseText(String text, Column column) {
		String value = text.strip();
		if (!yearMonth()) {
			DataType actual = type == DataType.INTERVAL ? DataType.INTERVAL_DAY_TO_SECOND : type;
			String qualifier = actual.name().substring(9);
			boolean day = qualifier.startsWith("DAY_TO_");
			String sign = value.startsWith("-") || value.startsWith("+") ? value.substring(0,1) : "";
			String unsigned = sign.isEmpty() ? value : value.substring(1);
			if (day && unsigned.contains(":" ) && !unsigned.contains(" ")) unsigned = "0 " + unsigned;
			if (day && !unsigned.contains(" ")) unsigned += " 0";
			int expected = qualifier.endsWith("SECOND") ? 3 : qualifier.endsWith("MINUTE") ? 2 : 1;
			if (qualifier.startsWith("MINUTE")) expected--;
			if (qualifier.contains("_TO_")) {
				int present = unsigned.split(":", -1).length;
				while (present++ < expected) unsigned += ":0";
			}
			value = sign + unsigned;
		}
		return codec(column).parse(value);
	}
	private String json(Object value, int depth, Column scalar) {
		if (value == null) return "null";
		if (depth == 0) return "\"" + codec(scalar).convert(value).text() + "\"";
		return "[" + java.util.Arrays.stream((Object[])value).map(item -> json(item, depth-1, scalar))
				.collect(java.util.stream.Collectors.joining(",")) + "]";
	}
	@Override public String readExpression(String quotedColumn, Column column) {
		return "CAST(" + quotedColumn + " AS " + "ARRAY[".repeat(column.getArrayDimension()) + "VARCHAR(128)" + "]".repeat(column.getArrayDimension()) + ")";
	}
	private int intervalType() {
		return switch (type) {
		case INTERVAL_YEAR -> com.vertica.jdbc.VerticaTypes.INTERVAL_YEAR;
		case INTERVAL_MONTH -> com.vertica.jdbc.VerticaTypes.INTERVAL_MONTH;
		case INTERVAL_YEAR_TO_MONTH -> com.vertica.jdbc.VerticaTypes.INTERVAL_YEAR_TO_MONTH;
		case INTERVAL_DAY -> com.vertica.jdbc.VerticaTypes.INTERVAL_DAY;
		case INTERVAL_HOUR -> com.vertica.jdbc.VerticaTypes.INTERVAL_HOUR;
		case INTERVAL_MINUTE -> com.vertica.jdbc.VerticaTypes.INTERVAL_MINUTE;
		case INTERVAL_SECOND -> com.vertica.jdbc.VerticaTypes.INTERVAL_SECOND;
		case INTERVAL_DAY_TO_HOUR -> com.vertica.jdbc.VerticaTypes.INTERVAL_DAY_TO_HOUR;
		case INTERVAL_DAY_TO_MINUTE -> com.vertica.jdbc.VerticaTypes.INTERVAL_DAY_TO_MINUTE;
		case INTERVAL_HOUR_TO_MINUTE -> com.vertica.jdbc.VerticaTypes.INTERVAL_HOUR_TO_MINUTE;
		case INTERVAL_HOUR_TO_SECOND -> com.vertica.jdbc.VerticaTypes.INTERVAL_HOUR_TO_SECOND;
		case INTERVAL_MINUTE_TO_SECOND -> com.vertica.jdbc.VerticaTypes.INTERVAL_MINUTE_TO_SECOND;
		default -> com.vertica.jdbc.VerticaTypes.INTERVAL_DAY_TO_SECOND;
		};
	}
	@Override public boolean requiresJdbcBinding(Object value, Column column, java.sql.DatabaseMetaData server){return yearMonth() && column.getArrayDimension()>0;}
	@Override public boolean supportsJdbcBinding(Column column) { return column.getArrayDimension() <= 1; }
	@Override public IntervalConversion prepareInterval(Object input, Column column) {
		if (column.getArrayDimension() > 0) {
			validateIntervalColumn(column);
			IntervalArrays.validateBounds(input, column.getArrayDimension(), column.getSpecifics().get(IntervalArrays.DECLARED_BOUNDS));
			var warnings = new java.util.LinkedHashSet<String>();
			var scalar = column.clone().setArrayDimension(0);
			Object values = IntervalArrays.map(input, column.getArrayDimension(), leaf -> {
				var result = prepareInterval(leaf, scalar); warnings.addAll(result.warnings()); return result.value();
			});
			return new IntervalConversion(values, java.util.List.copyOf(warnings));
		}
		var result = codec(column).convert(decode(input));
		if (yearMonth()) {
			// The public driver constructor has int year/month fields, unlike the server's int64 month storage.
			BigInteger.valueOf(result.value().months()).abs().divide(BigInteger.valueOf(12)).intValueExact();
		} else {
			var time = result.value().timePart();
			long micros = BigInteger.valueOf(time.getSeconds()).multiply(BigInteger.valueOf(1_000_000))
					.add(BigInteger.valueOf(time.getNano() / 1000)).longValueExact();
			if (micros == Long.MIN_VALUE) throw new ArithmeticException("Vertica cannot represent the int64 minimum as a non-null interval");
		}
		return new IntervalConversion(result.value(), result.warnings().stream().map(Enum::name).toList());
	}
	public Object nativeValue(Object input, Column column) {
		var value = (IntervalValue) prepareInterval(input, column).value();
		if (yearMonth()) {
			var magnitude = BigInteger.valueOf(value.months()).abs().divideAndRemainder(BigInteger.valueOf(12));
			return new VerticaYearMonthInterval(intervalType(), magnitude[0].intValueExact(), magnitude[1].intValueExact(), value.months() < 0);
		}
		var time = value.timePart().abs();
		long seconds = time.getSeconds();
		return new VerticaDayTimeInterval(intervalType(), Math.toIntExact(seconds / 86400), (int) (seconds / 3600 % 24),
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
		if (rows.getMetaData().getColumnType(index) == Types.VARCHAR) {
			String text = rows.getString(index);
			return text == null ? null : parseText(text, new Column().setDataType(type).setLength(6));
		}
		Object value = rows.getObject(index); return value == null ? null : decode(value);
	}
	@Override public Object getObject(ResultSet rows, int index, Column column) throws SQLException {
		if (column.getArrayDimension() > 0) {
			var scalar = column.clone().setArrayDimension(0);
			// The driver exposes null subarrays as empty java.sql.Array objects.
			// Its outer JSON text retains null versus empty at every depth.
			String text = rows.getString(index);
			return text == null ? null : IntervalArrays.map(ARRAY_JSON.fromJsonString(text, Object.class),
					column.getArrayDimension(), leaf -> parseText((String) leaf, scalar));
		}
		if (rows.getMetaData().getColumnType(index) == Types.VARCHAR) {
			String text = rows.getString(index); return text == null ? null : parseText(text, column);
		}
		return getObject(rows, index);
	}
	@Override public Object getObject(ResultSet rows, String name) throws SQLException {
		return getObject(rows, rows.findColumn(name));
	}
	@Override public void setObject(PreparedStatement statement, int index, Object value) throws SQLException {
		setObject(statement, index, value, new Column().setDataType(type));
	}
	@Override public void setObject(PreparedStatement statement, int index, Object value, Column column) throws SQLException {
		validateIntervalColumn(column);
		if (!supportsJdbcBinding(column)) throw new IllegalArgumentException("Nested Vertica intervals require NATIVE JSON COPY; one JDBC parameter cannot represent this declaration");
		if (value == null) statement.setNull(index, column.getArrayDimension()>0 ? Types.VARCHAR : Types.NULL);
		else if (column.getArrayDimension() > 0) statement.setString(index, json(prepareInterval(value,column).value(), column.getArrayDimension(), column.clone().setArrayDimension(0)));
		else statement.setObject(index, nativeValue(value, column));
	}
	/** Explicit CAST keeps JDBC batching on prepared INSERT rather than the driver's scalar COPY path. */
	public String arrayType(Column column) {
		validateIntervalColumn(column);
		String qualifier=type==DataType.INTERVAL?"DAY TO SECOND":type.name().substring(9).replace('_',' ');
		return "ARRAY[".repeat(column.getArrayDimension())+"INTERVAL "+qualifier
				+(qualifier.endsWith("SECOND")?"("+intervalPrecision(column).fractional()+")":"")+"]".repeat(column.getArrayDimension());
	}

	/** Exact server input for one scalar, also used by native bulk transport. */
	public String text(Object value, Column column) {
		if (column.getArrayDimension() != 0) throw new IllegalArgumentException("Expected a scalar interval column");
		var interval=(IntervalValue)prepareInterval(value,column).value();
		return codec(column).convert(interval).text();
	}

	public String literal(Object value, Column column) {
		if (value == null) return "NULL";
		if (column.getArrayDimension() > 0) {
			Object values = prepareInterval(value, column).value();
			var scalar = column.clone().setArrayDimension(0);
			return IntervalArrays.literal(values, column.getArrayDimension(), leaf -> literal(leaf, scalar));
		}
		var projected = prepareInterval(value, column).value();
		String qualifier = type == DataType.INTERVAL ? "DAY TO SECOND" : type.name().substring(9).replace('_', ' ');
		return "INTERVAL '" + codec(column).convert(projected).text() + "' " + qualifier
				+ (qualifier.endsWith("SECOND") ? "(" + intervalPrecision(column).fractional() + ")" : "");
	}
}
