/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-sqlite.
 *
 * sqlapp-core-sqlite is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlite is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlite.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sqlite.util;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.datatype.JdbcTypeHandler;

/**
 * Date and time handlers for SQLite, which has no date storage class.
 * <p>
 * Values are written as the ISO-8601 text understood by SQLite's date and time
 * functions and produced by {@code CURRENT_TIMESTAMP}: {@code YYYY-MM-DD},
 * {@code HH:MM:SS[.fff]} and {@code YYYY-MM-DD HH:MM:SS[.fff]}. A fraction is
 * written only when it is not zero. Reading also accepts the other storage
 * forms found in existing databases: INTEGER as Unix seconds, or as epoch
 * milliseconds when the magnitude is at least 10^11 (the form the Xerial driver
 * writes by default), and REAL as a Julian day number.
 */
public final class SqliteJdbcTypeHandlers {
	/** Integers at or above this magnitude are epoch milliseconds, not seconds. */
	private static final long MILLISECOND_THRESHOLD = 100_000_000_000L;

	private static final double JULIAN_DAY_OF_EPOCH = 2440587.5d;

	private static final Pattern DATE_TIME = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})"
			+ "(?:[ T](\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d{1,9})?)?))?\\s*(Z|z|[+-]\\d{2}:\\d{2})?$");

	private static final Pattern TIME = Pattern.compile("^\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d{1,9})?)?$");

	private SqliteJdbcTypeHandlers() {
	}

	/** A date-time text split into its local value and optional UTC offset. */
	private record Parsed(LocalDateTime dateTime, ZoneOffset offset) {
		Instant instant() {
			return dateTime.toInstant(offset);
		}
	}

	private static Parsed parse(final String type, final String text) throws SQLException {
		final String value = text.trim();
		final Matcher matcher = DATE_TIME.matcher(value);
		if (!matcher.matches())
			throw new SQLException("Cannot read SQLite " + type + " from text: " + text);
		try {
			final LocalDate date = LocalDate.parse(matcher.group(1));
			final LocalTime time = matcher.group(2) == null ? LocalTime.MIDNIGHT : LocalTime.parse(matcher.group(2));
			final String zone = matcher.group(3);
			final ZoneOffset offset = zone == null ? null
					: zone.equalsIgnoreCase("Z") ? ZoneOffset.UTC : ZoneOffset.of(zone);
			return new Parsed(LocalDateTime.of(date, time), offset);
		} catch (java.time.DateTimeException e) {
			throw new SQLException("Cannot read SQLite " + type + " from text: " + text, e);
		}
	}

	/** Unix seconds, epoch milliseconds or a Julian day number. */
	private static Instant instant(final Number value) {
		if (value instanceof Double || value instanceof Float || value instanceof java.math.BigDecimal)
			return Instant.ofEpochMilli(Math.round((value.doubleValue() - JULIAN_DAY_OF_EPOCH) * 86_400_000d));
		final long number = value.longValue();
		return Math.abs(number) >= MILLISECOND_THRESHOLD ? Instant.ofEpochMilli(number) : Instant.ofEpochSecond(number);
	}

	private static String format(final LocalTime time) {
		final StringBuilder builder = new StringBuilder(18);
		append(builder, time.getHour()).append(':');
		append(builder, time.getMinute()).append(':');
		append(builder, time.getSecond());
		final int nano = time.getNano();
		if (nano != 0) {
			// Milliseconds when exact, otherwise all nine digits so nothing is lost.
			builder.append('.');
			if (nano % 1_000_000 == 0)
				builder.append(String.format(java.util.Locale.ROOT, "%03d", nano / 1_000_000));
			else
				builder.append(String.format(java.util.Locale.ROOT, "%09d", nano));
		}
		return builder.toString();
	}

	private static StringBuilder append(final StringBuilder builder, final int twoDigits) {
		if (twoDigits < 10)
			builder.append('0');
		return builder.append(twoDigits);
	}

	private static String format(final LocalDateTime dateTime) {
		return dateTime.toLocalDate() + " " + format(dateTime.toLocalTime());
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
				stmt.setNull(parameterIndex, Types.VARCHAR);
			} else {
				stmt.setString(parameterIndex, toText(x));
			}
		}

		/** The ISO-8601 text stored for a non-null value. */
		protected abstract String toText(Object x) throws SQLException;

		protected static SQLException unsupported(final String type, final Object x) {
			return new SQLException("Unsupported value for SQLite " + type + ": " + x.getClass().getName());
		}

		/** Local date-time of a value in the JVM default time zone. */
		protected static LocalDateTime local(final Instant instant) {
			return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
		}
	}

	/** DATE as {@code YYYY-MM-DD}, read as {@link java.sql.Date}. */
	public static class DateHandler extends AbstractHandler {
		private static final long serialVersionUID = 1L;

		@Override
		public Object getObject(final ResultSet rs, final int columnIndex) throws SQLException {
			final Object value = rs.getObject(columnIndex);
			if (value == null)
				return null;
			if (value instanceof Number number)
				return java.sql.Date.valueOf(local(instant(number)).toLocalDate());
			return java.sql.Date.valueOf(parse("DATE", value.toString()).dateTime().toLocalDate());
		}

		@Override
		protected String toText(final Object x) throws SQLException {
			final LocalDate value;
			if (x instanceof LocalDate date) {
				value = date;
			} else if (x instanceof java.sql.Date date) {
				value = date.toLocalDate();
			} else if (x instanceof Timestamp timestamp) {
				value = timestamp.toLocalDateTime().toLocalDate();
			} else if (x instanceof LocalDateTime dateTime) {
				value = dateTime.toLocalDate();
			} else if (x instanceof OffsetDateTime dateTime) {
				value = dateTime.toLocalDate();
			} else if (x instanceof ZonedDateTime dateTime) {
				value = dateTime.toLocalDate();
			} else if (x instanceof Instant instant) {
				value = local(instant).toLocalDate();
			} else if (x instanceof java.util.Date date) {
				value = local(Instant.ofEpochMilli(date.getTime())).toLocalDate();
			} else if (x instanceof CharSequence text) {
				value = parse("DATE", text.toString()).dateTime().toLocalDate();
			} else {
				throw unsupported("DATE", x);
			}
			return value.toString();
		}
	}

	/** TIME as {@code HH:MM:SS[.fff]}, read as {@link LocalTime}. */
	public static class TimeHandler extends AbstractHandler {
		private static final long serialVersionUID = 1L;

		@Override
		public Object getObject(final ResultSet rs, final int columnIndex) throws SQLException {
			final Object value = rs.getObject(columnIndex);
			if (value == null)
				return null;
			if (value instanceof Number number)
				return local(instant(number)).toLocalTime();
			return parseTime(value.toString());
		}

		private static LocalTime parseTime(final String text) throws SQLException {
			final String value = text.trim();
			if (TIME.matcher(value).matches()) {
				try {
					return LocalTime.parse(value);
				} catch (java.time.DateTimeException e) {
					throw new SQLException("Cannot read SQLite TIME from text: " + text, e);
				}
			}
			return parse("TIME", value).dateTime().toLocalTime();
		}

		@Override
		protected String toText(final Object x) throws SQLException {
			final LocalTime value;
			if (x instanceof LocalTime time) {
				value = time;
			} else if (x instanceof java.sql.Time time) {
				value = time.toLocalTime().withNano((int) Math.floorMod(time.getTime(), 1000L) * 1_000_000);
			} else if (x instanceof Timestamp timestamp) {
				value = timestamp.toLocalDateTime().toLocalTime();
			} else if (x instanceof LocalDateTime dateTime) {
				value = dateTime.toLocalTime();
			} else if (x instanceof OffsetDateTime dateTime) {
				value = dateTime.toLocalTime();
			} else if (x instanceof ZonedDateTime dateTime) {
				value = dateTime.toLocalTime();
			} else if (x instanceof CharSequence text) {
				value = parseTime(text.toString());
			} else {
				throw unsupported("TIME", x);
			}
			return format(value);
		}
	}

	/**
	 * TIMESTAMP as {@code YYYY-MM-DD HH:MM:SS[.fff]}, read as {@link Timestamp}.
	 * Text carrying a UTC offset, like a number, denotes an instant.
	 */
	public static class TimestampHandler extends AbstractHandler {
		private static final long serialVersionUID = 1L;

		@Override
		public Object getObject(final ResultSet rs, final int columnIndex) throws SQLException {
			final Object value = rs.getObject(columnIndex);
			if (value == null)
				return null;
			if (value instanceof Number number)
				return Timestamp.from(instant(number));
			final Parsed parsed = parse("TIMESTAMP", value.toString());
			return parsed.offset() == null ? Timestamp.valueOf(parsed.dateTime()) : Timestamp.from(parsed.instant());
		}

		@Override
		protected String toText(final Object x) throws SQLException {
			final LocalDateTime value;
			if (x instanceof Timestamp timestamp) {
				value = timestamp.toLocalDateTime();
			} else if (x instanceof LocalDateTime dateTime) {
				value = dateTime;
			} else if (x instanceof LocalDate date) {
				value = date.atStartOfDay();
			} else if (x instanceof java.sql.Date date) {
				value = date.toLocalDate().atStartOfDay();
			} else if (x instanceof OffsetDateTime dateTime) {
				value = dateTime.toLocalDateTime();
			} else if (x instanceof ZonedDateTime dateTime) {
				value = dateTime.toLocalDateTime();
			} else if (x instanceof Instant instant) {
				value = local(instant);
			} else if (x instanceof java.util.Date date) {
				value = local(Instant.ofEpochMilli(date.getTime()));
			} else if (x instanceof CharSequence text) {
				final Parsed parsed = parse("TIMESTAMP", text.toString());
				value = parsed.offset() == null ? parsed.dateTime() : local(parsed.instant());
			} else {
				throw unsupported("TIMESTAMP", x);
			}
			return format(value);
		}
	}

	/**
	 * TIMESTAMP WITH TIME ZONE as {@code YYYY-MM-DD HH:MM:SS[.fff]+HH:MM}, read as
	 * {@link OffsetDateTime}. The offset is retained; text without one is UTC, as
	 * in SQLite's own date functions.
	 */
	public static class TimestampWithTimeZoneHandler extends AbstractHandler {
		private static final long serialVersionUID = 1L;

		@Override
		public Object getObject(final ResultSet rs, final int columnIndex) throws SQLException {
			final Object value = rs.getObject(columnIndex);
			if (value == null)
				return null;
			if (value instanceof Number number)
				return instant(number).atOffset(ZoneOffset.UTC);
			return offset(value.toString());
		}

		private static OffsetDateTime offset(final String text) throws SQLException {
			final Parsed parsed = parse("TIMESTAMP WITH TIME ZONE", text);
			return parsed.dateTime().atOffset(parsed.offset() == null ? ZoneOffset.UTC : parsed.offset());
		}

		@Override
		protected String toText(final Object x) throws SQLException {
			final OffsetDateTime value;
			if (x instanceof OffsetDateTime dateTime) {
				value = dateTime;
			} else if (x instanceof ZonedDateTime dateTime) {
				value = dateTime.toOffsetDateTime();
			} else if (x instanceof Instant instant) {
				value = instant.atOffset(ZoneOffset.UTC);
			} else if (x instanceof Timestamp timestamp) {
				value = timestamp.toInstant().atOffset(ZoneOffset.UTC);
			} else if (x instanceof java.util.Date date) {
				value = Instant.ofEpochMilli(date.getTime()).atOffset(ZoneOffset.UTC);
			} else if (x instanceof CharSequence text) {
				value = offset(text.toString());
			} else {
				throw unsupported("TIMESTAMP WITH TIME ZONE", x);
			}
			final ZoneOffset offset = value.getOffset();
			// SQLite accepts [+-]HH:MM only; ZoneOffset prints UTC as "Z" and may add
			// seconds.
			final int minutes = offset.getTotalSeconds() / 60;
			final StringBuilder builder = new StringBuilder(format(value.toLocalDateTime()));
			builder.append(minutes < 0 ? '-' : '+');
			append(builder, Math.abs(minutes) / 60).append(':');
			append(builder, Math.abs(minutes) % 60);
			return builder.toString();
		}
	}
}
