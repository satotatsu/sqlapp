/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.util;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.datatype.JdbcTypeHandler;
import com.sqlapp.data.interval.Interval;

/**
 * JDBC bindings for DuckDB types whose generic handling loses data or is
 * rejected by the DuckDB JDBC driver.
 * <p>
 * Values are bound as text and converted by DuckDB to the parameter's column
 * type, which behaves the same with every driver from 1.0 to 2.0:
 * </p>
 * <ul>
 * <li>HUGEINT / UBIGINT: the driver rejects BigInteger with a target type and
 * silently stores NULL for a BigDecimal bound to HUGEINT.</li>
 * <li>DATE / TIME: drivers before 1.2 / 1.4 reject {@code LocalDate} /
 * {@code LocalTime}; {@link java.sql.Time} drops microseconds, so TIME values
 * are read as {@link LocalTime}.</li>
 * <li>TIMESTAMPTZ: read and written as {@link java.time.OffsetDateTime} so the
 * instant does not depend on the JVM or session time zone.</li>
 * <li>UUID: the 1.0 driver changes the high bit of a {@link UUID} bound with
 * {@code setObject}.</li>
 * <li>BLOB: bound with {@code setBytes} and read with {@code getBytes}.</li>
 * <li>INTERVAL: read from DuckDB's text form into {@link Interval}; an interval
 * whose months, days and time have different signs cannot be represented by
 * {@link Interval} and is kept as DuckDB text. Written as months, days and
 * microseconds.</li>
 * </ul>
 */
public final class DuckDbJdbcTypeHandlers {

	/** DuckDB requires seconds; {@link LocalTime#toString()} omits zero seconds. */
	private static final java.time.format.DateTimeFormatter TIME_FORMAT = java.time.format.DateTimeFormatter
			.ofPattern("HH:mm:ss.SSSSSS");

	/** DuckDB's TIMESTAMPTZ input format with an explicit offset. */
	private static final java.time.format.DateTimeFormatter TIMESTAMP_WITH_TIME_ZONE_FORMAT = java.time.format.DateTimeFormatter
			.ofPattern("uuuu-MM-dd HH:mm:ss.SSSSSSxxx");

	private DuckDbJdbcTypeHandlers() {
	}

	private abstract static class AbstractHandler implements JdbcTypeHandler, Serializable {
		private static final long serialVersionUID = 1L;

		@Override
		public Object getObject(final ResultSet rs, final String columnLabel) throws SQLException {
			return getObject(rs, rs.findColumn(columnLabel));
		}

		@Override
		public void setObject(final PreparedStatement stmt, final int parameterIndex, final Object x)
				throws SQLException {
			if (x == null) {
				stmt.setNull(parameterIndex, Types.OTHER);
			} else {
				setNonNull(stmt, parameterIndex, x);
			}
		}

		protected abstract void setNonNull(PreparedStatement stmt, int parameterIndex, Object x) throws SQLException;

		protected static SQLException unsupported(final String type, final Object x) {
			return new SQLException("Unsupported value for DuckDB " + type + ": " + x.getClass().getName());
		}
	}

	/** HUGEINT and UBIGINT. */
	public static class BigIntegerHandler extends AbstractHandler {
		private static final long serialVersionUID = 1L;

		@Override
		public Object getObject(final ResultSet rs, final int columnIndex) throws SQLException {
			final Object value = rs.getObject(columnIndex);
			if (value == null || value instanceof BigInteger) {
				return value;
			}
			if (value instanceof BigDecimal decimal) {
				return decimal.toBigIntegerExact();
			}
			return new BigInteger(value.toString());
		}

		@Override
		protected void setNonNull(final PreparedStatement stmt, final int parameterIndex, final Object x)
				throws SQLException {
			final BigInteger value;
			if (x instanceof BigInteger integer) {
				value = integer;
			} else if (x instanceof BigDecimal decimal) {
				value = decimal.toBigIntegerExact();
			} else if (x instanceof Long || x instanceof Integer || x instanceof Short || x instanceof Byte) {
				value = BigInteger.valueOf(((Number) x).longValue());
			} else if (x instanceof CharSequence text) {
				value = new BigInteger(text.toString().trim());
			} else {
				throw unsupported("HUGEINT/UBIGINT", x);
			}
			stmt.setString(parameterIndex, value.toString());
		}
	}

	/** DATE, read as {@link java.sql.Date}. */
	public static class DateHandler extends AbstractHandler {
		private static final long serialVersionUID = 1L;

		@Override
		public Object getObject(final ResultSet rs, final int columnIndex) throws SQLException {
			final Object value = rs.getObject(columnIndex);
			if (value == null) {
				return null;
			}
			return java.sql.Date.valueOf(value instanceof LocalDate date ? date : LocalDate.parse(value.toString()));
		}

		@Override
		protected void setNonNull(final PreparedStatement stmt, final int parameterIndex, final Object x)
				throws SQLException {
			final LocalDate value;
			if (x instanceof LocalDate date) {
				value = date;
			} else if (x instanceof java.sql.Date date) {
				value = date.toLocalDate();
			} else if (x instanceof java.sql.Timestamp timestamp) {
				value = timestamp.toLocalDateTime().toLocalDate();
			} else if (x instanceof LocalDateTime dateTime) {
				value = dateTime.toLocalDate();
			} else if (x instanceof java.util.Date date) {
				value = new java.sql.Date(date.getTime()).toLocalDate();
			} else if (x instanceof CharSequence text) {
				value = LocalDate.parse(text.toString().trim());
			} else {
				throw unsupported("DATE", x);
			}
			stmt.setString(parameterIndex, value.toString());
		}
	}

	/** TIME, read as {@link LocalTime} to keep microseconds. */
	public static class TimeHandler extends AbstractHandler {
		private static final long serialVersionUID = 1L;

		@Override
		public Object getObject(final ResultSet rs, final int columnIndex) throws SQLException {
			final Object value = rs.getObject(columnIndex);
			if (value == null || value instanceof LocalTime) {
				return value;
			}
			return LocalTime.parse(value.toString());
		}

		@Override
		protected void setNonNull(final PreparedStatement stmt, final int parameterIndex, final Object x)
				throws SQLException {
			final LocalTime value;
			if (x instanceof LocalTime time) {
				value = time;
			} else if (x instanceof java.sql.Time time) {
				value = time.toLocalTime().withNano((int) Math.floorMod(time.getTime(), 1000L) * 1_000_000);
			} else if (x instanceof java.sql.Timestamp timestamp) {
				value = timestamp.toLocalDateTime().toLocalTime();
			} else if (x instanceof LocalDateTime dateTime) {
				value = dateTime.toLocalTime();
			} else if (x instanceof CharSequence text) {
				value = LocalTime.parse(text.toString().trim());
			} else {
				throw unsupported("TIME", x);
			}
			stmt.setString(parameterIndex, TIME_FORMAT.format(value));
		}
	}

	/** TIMESTAMP WITH TIME ZONE, read as {@link java.time.OffsetDateTime}. */
	public static class TimestampWithTimeZoneHandler extends AbstractHandler {
		private static final long serialVersionUID = 1L;

		@Override
		public Object getObject(final ResultSet rs, final int columnIndex) throws SQLException {
			final Object value = rs.getObject(columnIndex);
			if (value == null || value instanceof java.time.OffsetDateTime) {
				return value;
			}
			return java.time.OffsetDateTime.parse(value.toString());
		}

		@Override
		protected void setNonNull(final PreparedStatement stmt, final int parameterIndex, final Object x)
				throws SQLException {
			final java.time.OffsetDateTime value;
			if (x instanceof java.time.OffsetDateTime dateTime) {
				value = dateTime;
			} else if (x instanceof java.time.ZonedDateTime dateTime) {
				value = dateTime.toOffsetDateTime();
			} else if (x instanceof java.time.Instant instant) {
				value = instant.atOffset(java.time.ZoneOffset.UTC);
			} else if (x instanceof java.util.Date date) {
				// java.sql.Timestamp and java.util.Date denote an instant.
				final java.time.Instant instant = date instanceof java.sql.Timestamp timestamp ? timestamp.toInstant()
						: date.toInstant();
				value = instant.atOffset(java.time.ZoneOffset.UTC);
			} else if (x instanceof CharSequence text) {
				value = java.time.OffsetDateTime.parse(text.toString().trim());
			} else {
				throw unsupported("TIMESTAMPTZ", x);
			}
			stmt.setString(parameterIndex, TIMESTAMP_WITH_TIME_ZONE_FORMAT.format(value));
		}
	}

	/** UUID. */
	public static class UuidHandler extends AbstractHandler {
		private static final long serialVersionUID = 1L;

		@Override
		public Object getObject(final ResultSet rs, final int columnIndex) throws SQLException {
			final Object value = rs.getObject(columnIndex);
			return value == null || value instanceof UUID ? value : UUID.fromString(value.toString());
		}

		@Override
		protected void setNonNull(final PreparedStatement stmt, final int parameterIndex, final Object x)
				throws SQLException {
			if (x instanceof UUID uuid) {
				stmt.setString(parameterIndex, uuid.toString());
			} else if (x instanceof CharSequence text) {
				stmt.setString(parameterIndex, UUID.fromString(text.toString().trim()).toString());
			} else {
				throw unsupported("UUID", x);
			}
		}
	}

	/** BLOB. */
	public static class BlobHandler extends AbstractHandler {
		private static final long serialVersionUID = 1L;

		@Override
		public Object getObject(final ResultSet rs, final int columnIndex) throws SQLException {
			return rs.getBytes(columnIndex);
		}

		@Override
		protected void setNonNull(final PreparedStatement stmt, final int parameterIndex, final Object x)
				throws SQLException {
			if (x instanceof byte[] bytes) {
				stmt.setBytes(parameterIndex, bytes);
			} else if (x instanceof java.sql.Blob blob) {
				stmt.setBytes(parameterIndex, blob.getBytes(1, (int) blob.length()));
			} else {
				throw unsupported("BLOB", x);
			}
		}
	}

	/** Exact interval adapter; legacy static helpers delegate to the same codec. */
	public static class IntervalHandler extends com.sqlapp.data.db.dialect.duckdb.DuckDbIntervalJdbcTypeHandler {
		private static final long serialVersionUID = 1L;
		public static Object parse(String text) { return com.sqlapp.data.db.dialect.duckdb.DuckDbIntervalCodec.decode(text); }
		public static String format(Interval value) { return com.sqlapp.data.db.dialect.duckdb.DuckDbIntervalCodec.encode(value); }
	}}
