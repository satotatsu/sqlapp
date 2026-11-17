/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.postgresql.util.PGInterval;
import org.postgresql.util.PGobject;

import com.sqlapp.data.converter.IntervalValueConverter;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.MetaType;
import com.sqlapp.data.interval.IntervalValue;
import com.sqlapp.data.schemas.Column;

/**
 * PostgreSQL month/day/microsecond representation. Never exchanges calendar days
 * for hours and never uses double for fractions or a 30-day month approximation.
 */
public final class PostgresIntervalCodec {
	private static final BigInteger BILLION = BigInteger.valueOf(1_000_000_000);
	private static final Pattern TIME = Pattern.compile("([+-]?)([0-9]+):([0-5][0-9]):([0-5][0-9](?:\\.[0-9]{1,9})?)");
	private static final Pattern YM = Pattern.compile("([+-]?)([0-9]+)-([0-9]+)");
	private static final Pattern SQL = Pattern.compile(
			"(?:(?<ym>[+-]?[0-9]+-[0-9]+)\\s+)?(?:(?<day>[+-]?[0-9]+)\\s+)?(?<time>[+-]?[0-9]+:[0-5][0-9]:[0-5][0-9](?:\\.[0-9]{1,9})?)");
	private static final Pattern FIELD = Pattern.compile(
			"([+-]?[0-9]+(?:\\.[0-9]{1,9})?)\\s+(years?|mons?|months?|days?|hours?|minutes?|mins?|seconds?|secs?|microseconds?)(?=\\s|$)",
			Pattern.CASE_INSENSITIVE);

	private PostgresIntervalCodec() {
	}

	public static IntervalValue validate(final IntervalValue value) {
		value.requireFractionalPrecision(6);
		Math.toIntExact(value.months());
		Math.toIntExact(value.days());
		final long time = micros(value.timePart()).longValueExact();
		if ((value.months() == Integer.MAX_VALUE && value.days() == Integer.MAX_VALUE && time == Long.MAX_VALUE)
				|| (value.months() == Integer.MIN_VALUE && value.days() == Integer.MIN_VALUE && time == Long.MIN_VALUE)) {
			throw new IllegalArgumentException("PostgreSQL reserves this interval for infinity; finite IntervalValue required");
		}
		return value;
	}

	/** PostgreSQL field restrictions discard only fields to the right of the last unit. */
	public static IntervalValue validate(final IntervalValue value, final DataType type, final int precision) {
		validate(value);
		if (precision < 0 || precision > 6) throw new IllegalArgumentException("PostgreSQL interval precision must be 0..6");
		value.requireFractionalPrecision(precision);
		final String last = type.getTypeName();
		if (type == DataType.INTERVAL) return value;
		if (type.getMetaTtype() != MetaType.INTERVAL) {
			throw new IllegalArgumentException("Expected an interval Schema type");
		}
		if (last.endsWith("YEAR") || last.endsWith("MONTH")) {
			if (value.days() != 0 || !value.timePart().isZero()
					|| (last.endsWith("YEAR") && value.months() % 12 != 0)) throw fieldLoss(type);
		} else if (last.endsWith("DAY")) {
			if (!value.timePart().isZero()) throw fieldLoss(type);
		} else if (last.endsWith("HOUR") || last.endsWith("MINUTE")) {
			final Duration unit = Duration.ofSeconds(last.endsWith("HOUR") ? 3600 : 60);
			if (!unit.multipliedBy(value.timePart().dividedBy(unit)).equals(value.timePart())) throw fieldLoss(type);
		}
		return value;
	}

	private static IllegalArgumentException fieldLoss(final DataType type) {
		return new IllegalArgumentException("Interval would lose fields when written to " + type.getTypeName());
	}

	public static String encode(final IntervalValue value, final Column column) {
		final Long specified = column.getLength();
		final int precision = specified == null ? 6 : Math.toIntExact(specified);
		validate(value, column.getDataType(), precision);
		return encode(value);
	}

	private static BigInteger micros(final Duration time) {
		return BigInteger.valueOf(time.getSeconds()).multiply(BigInteger.valueOf(1_000_000))
				.add(BigInteger.valueOf(time.getNano() / 1000));
	}

	public static IntervalValue decode(final Object input) {
		if (input == null) return null;
		if (input instanceof PGInterval value) {
			if (value.getValue() == null) return null;
			return validate(new IntervalValue(Math.addExact(Math.multiplyExact((long) value.getYears(), 12), value.getMonths()),
					value.getDays(), Duration.ofHours(value.getHours()).plusMinutes(value.getMinutes())
						.plusSeconds(value.getWholeSeconds()).plusNanos((long) value.getMicroSeconds() * 1000)));
		}
		if (input instanceof PGobject value) {
			if (!"interval".equalsIgnoreCase(value.getType())) {
				throw new IllegalArgumentException("Expected PostgreSQL interval, got " + value.getType());
			}
			return value.getValue() == null ? null : parse(value.getValue());
		}
		if (input instanceof String text) return parse(text);
		return validate(new IntervalValueConverter().convertObject(input));
	}

	/** Explicit signs keep input independent of the connection's IntervalStyle. */
	public static String encode(final IntervalValue value) {
		validate(value);
		return signed(Long.toString(value.months())) + " mons " + signed(Long.toString(value.days()))
				+ " days " + signed(micros(value.timePart()).toString()) + " microseconds";
	}

	private static String signed(final String number) {
		return number.startsWith("-") ? number : "+" + number;
	}

	/** Reads the four server output styles and the codec's explicit labeled text. */
	public static IntervalValue parse(final String input) {
		String text = input.trim();
		if (text.startsWith("P") || text.startsWith("p")) {
			return validate(IntervalValue.parse(text));
		}
		final Matcher yearMonth = YM.matcher(text);
		if (yearMonth.matches()) {
			return validate(new IntervalValue(months(yearMonth), 0, Duration.ZERO));
		}
		final Matcher sql = SQL.matcher(text);
		if (sql.matches()) {
			long months = 0;
			if (sql.group("ym") != null) {
				final Matcher ym = YM.matcher(sql.group("ym"));
				ym.matches();
				months = months(ym);
			}
			long days = sql.group("day") == null ? 0 : Long.parseLong(sql.group("day"));
			Duration time = time(sql.group("time"));
			// Unlabeled day/time is sql_standard output, whose lone leading minus
			// applies to all components. Explicit signs preserve independent fields.
			if (text.startsWith("-") && !hasSign(sql.group("time"))) {
				if (sql.group("ym") == null || !hasSign(sql.group("day"))) {
					time = time.negated();
					if (sql.group("ym") != null) days = -days;
				}
			}
			return validate(new IntervalValue(months, days, time));
		}
		boolean ago = text.toLowerCase(Locale.ROOT).endsWith(" ago");
		if (ago) text = text.substring(0, text.length() - 4).trim();
		if (text.startsWith("@")) text = text.substring(1).trim();
		if (text.equals("0")) return IntervalValue.ZERO;
		long months = 0;
		long days = 0;
		Duration time = Duration.ZERO;
		int position = 0;
		boolean found = false;
		while (position < text.length()) {
			final Matcher field = FIELD.matcher(text).region(position, text.length());
			if (field.lookingAt()) {
				final BigDecimal number = new BigDecimal(field.group(1));
				final String unit = field.group(2).toLowerCase(Locale.ROOT);
				if (unit.startsWith("year")) months = Math.addExact(months, Math.multiplyExact(number.longValueExact(), 12));
				else if (unit.startsWith("mon")) months = Math.addExact(months, number.longValueExact());
				else if (unit.startsWith("day")) days = Math.addExact(days, number.longValueExact());
				else if (unit.startsWith("hour")) time = time.plus(duration(number.multiply(BigDecimal.valueOf(3600))));
				else if (unit.startsWith("min")) time = time.plus(duration(number.multiply(BigDecimal.valueOf(60))));
				else if (unit.startsWith("micro")) time = time.plus(duration(number.movePointLeft(6)));
				else time = time.plus(duration(number));
				position = field.end();
			} else {
				final Matcher clock = TIME.matcher(text).region(position, text.length());
				if (!clock.lookingAt()) throw invalid(input);
				time = time.plus(time(clock.group()));
				position = clock.end();
			}
			found = true;
			if (position < text.length() && !Character.isWhitespace(text.charAt(position))) throw invalid(input);
			while (position < text.length() && Character.isWhitespace(text.charAt(position))) position++;
		}
		if (!found) throw invalid(input);
		final IntervalValue result = new IntervalValue(months, days, time);
		return validate(ago ? result.negated() : result);
	}

	private static boolean hasSign(final String text) {
		return text != null && (text.startsWith("+") || text.startsWith("-"));
	}

	private static long months(final Matcher match) {
		final long value = Math.addExact(Math.multiplyExact(Long.parseLong(match.group(2)), 12), Long.parseLong(match.group(3)));
		return match.group(1).equals("-") ? Math.negateExact(value) : value;
	}

	private static Duration time(final String text) {
		final Matcher match = TIME.matcher(text);
		if (!match.matches()) throw invalid(text);
		final Duration value = Duration.ofHours(Long.parseLong(match.group(2)))
				.plusMinutes(Long.parseLong(match.group(3))).plus(duration(new BigDecimal(match.group(4))));
		return match.group(1).equals("-") ? value.negated() : value;
	}

	private static Duration duration(final BigDecimal seconds) {
		final BigInteger[] parts = seconds.movePointRight(9).toBigIntegerExact().divideAndRemainder(BILLION);
		return Duration.ofSeconds(parts[0].longValueExact(), parts[1].longValueExact());
	}

	private static IllegalArgumentException invalid(final String text) {
		return new IllegalArgumentException("Unsupported PostgreSQL interval text (finite server output or sqlapp format required): " + text);
	}
}
