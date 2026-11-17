/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.interval;

import java.io.Serializable;
import java.math.BigInteger;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable interval with independently signed months, calendar days and time.
 * Days are never exchanged for 24-hour blocks. Equality compares these three
 * components; there is deliberately no database-independent natural ordering.
 * This value is independent of the legacy {@link Interval} API and JDBC codecs.
 *
 * @param months total months (years are combined into this field)
 * @param days calendar days, separate from the time part
 * @param timePart seconds and nanoseconds, including time amounts over 24 hours
 */
public record IntervalValue(long months, long days, Duration timePart) implements Serializable {
	private static final long serialVersionUID = 1L;
	private static final BigInteger BILLION = BigInteger.valueOf(1_000_000_000L);
	private static final BigInteger TWELVE = BigInteger.valueOf(12);
	private static final Pattern TEXT = Pattern.compile(
			"P(?:([+-]?[0-9]+)Y)?(?:([+-]?[0-9]+)M)?(?:([+-]?[0-9]+)D)?"
					+ "(?:T(?:([+-]?[0-9]+)H)?(?:([+-]?[0-9]+)M)?(?:([+-]?[0-9]+(?:\\.[0-9]{1,9})?)S)?)?",
			Pattern.CASE_INSENSITIVE);

	public static final IntervalValue ZERO = new IntervalValue(0, 0, Duration.ZERO);

	public IntervalValue {
		Objects.requireNonNull(timePart, "timePart");
	}

	/**
	 * Parses the sqlapp interval format based on ISO 8601, with independently
 * signed components. Fractions are allowed only on seconds (at most nine
 * digits). A D component stays in days; time components stay in timePart.
 * Legacy SQL interval strings are not accepted by this new parser.
 *
 * @throws DateTimeParseException for invalid syntax
 * @throws ArithmeticException if a resulting component exceeds its range
 */
	public static IntervalValue parse(final String text) {
		Objects.requireNonNull(text, "text");
		final Matcher matcher = TEXT.matcher(text);
		if (!matcher.matches()) {
			throw invalid(text);
		}
		boolean date = false;
		boolean time = false;
		for (int index = 1; index <= 3; index++) {
			date |= matcher.group(index) != null;
		}
		for (int index = 4; index <= 6; index++) {
			time |= matcher.group(index) != null;
		}
		if ((!date && !time) || ((text.indexOf('T') >= 0 || text.indexOf('t') >= 0) && !time)) {
			throw invalid(text);
		}
		final long months = integer(matcher.group(1)).multiply(TWELVE)
				.add(integer(matcher.group(2))).longValueExact();
		final long days = integer(matcher.group(3)).longValueExact();
		BigInteger nanos = integer(matcher.group(4)).multiply(BigInteger.valueOf(3600))
				.add(integer(matcher.group(5)).multiply(BigInteger.valueOf(60))).multiply(BILLION);
		if (matcher.group(6) != null) {
			// BigDecimal is used only for exact decimal scaling, never floating point.
			nanos = nanos.add(new java.math.BigDecimal(matcher.group(6)).movePointRight(9).toBigIntegerExact());
		}
		final BigInteger[] secondsAndNanos = nanos.divideAndRemainder(BILLION);
		return new IntervalValue(months, days,
				Duration.ofSeconds(secondsAndNanos[0].longValueExact(), secondsAndNanos[1].longValueExact()));
	}

	private static BigInteger integer(final String text) {
		return text == null ? BigInteger.ZERO : new BigInteger(text);
	}

	private static DateTimeParseException invalid(final String text) {
		return new DateTimeParseException("Invalid sqlapp interval; expected P[nY][nM][nD][T[nH][nM][n[.fraction]S]]",
				text, 0);
	}

	/** Adds corresponding components, throwing ArithmeticException on overflow. */
	public IntervalValue plus(final IntervalValue other) {
		Objects.requireNonNull(other, "other");
		return new IntervalValue(Math.addExact(months, other.months), Math.addExact(days, other.days),
				timePart.plus(other.timePart));
	}

	/** Subtracts corresponding components, throwing ArithmeticException on overflow. */
	public IntervalValue minus(final IntervalValue other) {
		Objects.requireNonNull(other, "other");
		return new IntervalValue(Math.subtractExact(months, other.months), Math.subtractExact(days, other.days),
				timePart.minus(other.timePart));
	}

	/** Multiplies all components, throwing ArithmeticException on overflow. */
	public IntervalValue multipliedBy(final long multiplier) {
		return new IntervalValue(Math.multiplyExact(months, multiplier), Math.multiplyExact(days, multiplier),
				timePart.multipliedBy(multiplier));
	}

	/** Reverses all signs, throwing ArithmeticException on overflow. */
	public IntervalValue negated() {
		return multipliedBy(-1);
	}

	/**
	 * Requires exact representability at the requested fractional-second precision.
 * For example, a microsecond adapter uses 6. This never rounds or truncates and
 * does not validate any database-specific range or interval qualifier.
 *
 * @return this unchanged value
 * @throws IllegalArgumentException unless fractionalDigits is between 0 and 9
 * @throws ArithmeticException if nonzero digits would be discarded
 */
	public IntervalValue requireFractionalPrecision(final int fractionalDigits) {
		if (fractionalDigits < 0 || fractionalDigits > 9) {
			throw new IllegalArgumentException("fractionalDigits must be between 0 and 9");
		}
		int quantum = 1;
		for (int index = fractionalDigits; index < 9; index++) {
			quantum *= 10;
		}
		if (timePart.getNano() % quantum != 0) {
			throw new ArithmeticException("Interval loses fractional seconds at precision " + fractionalDigits);
		}
		return this;
	}

	/**
	 * Converts a date-only value to Period, splitting total months into years and
 * residual months. Throws DateTimeException for a nonzero time part and
 * ArithmeticException if Period's int fields cannot hold the result.
 */
	public Period toPeriod() {
		if (!timePart.isZero()) {
			throw new DateTimeException("Cannot convert an interval with a time part to Period");
		}
		return Period.of(Math.toIntExact(months / 12), (int) (months % 12), Math.toIntExact(days));
	}

	/**
	 * Returns a time-only value as Duration. Months and calendar days are never
 * guessed or implicitly converted; a nonzero date part throws DateTimeException.
 */
	public Duration toDuration() {
		if (months != 0 || days != 0) {
			throw new DateTimeException("Cannot convert calendar months or days to Duration without an explicit policy");
		}
		return timePart;
	}

	/**
	 * Canonical sqlapp text: total months M, calendar days D, and total seconds S.
 * Zero components are omitted; zero is PT0S. Fractional trailing zeros are
 * omitted. Does not use the default locale or preserve input spelling.
 */
	@Override
	public String toString() {
		final StringBuilder text = new StringBuilder("P");
		if (months != 0) {
			text.append(months).append('M');
		}
		if (days != 0) {
			text.append(days).append('D');
		}
		if (!timePart.isZero()) {
			final BigInteger nanos = BigInteger.valueOf(timePart.getSeconds()).multiply(BILLION)
					.add(BigInteger.valueOf(timePart.getNano()));
			text.append('T').append(new java.math.BigDecimal(nanos, 9).stripTrailingZeros().toPlainString()).append('S');
		}
		return text.length() == 1 ? "PT0S" : text.toString();
	}
}
