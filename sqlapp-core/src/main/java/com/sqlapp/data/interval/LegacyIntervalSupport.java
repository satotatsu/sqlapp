/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.interval;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsing and exact component construction shared by the legacy facades. */
final class LegacyIntervalSupport {
	private static final String UNIT = "YEAR|MONTH|DAY|HOUR|MINUTE|SECOND";
	private static final Pattern SQL = Pattern.compile(
			"\\s*(?:INTERVAL\\s+)?(?:'([^']*)'|([+\\-0-9][0-9+ .:\\-]*))\\s*"
					+ "(?:(" + UNIT + ")(?:\\(([0-9]+)(?:,([0-9]+))?\\))?"
					+ "(?:\\s+TO\\s+(" + UNIT + ")(?:\\(([0-9]+)\\))?)?)?\\s*",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern NUMBER = Pattern.compile("[0-9]+(?:\\.[0-9]{0,9})?");
	private static final Pattern COMPLETE = Pattern.compile(
			"[+-]?[0-9]+-[0-9]+-[0-9]+\\s+[0-9]+:[0-9]+:[0-9]+(?:\\.[0-9]{0,9})?");
	private static final String[] UNITS = { "YEAR", "MONTH", "DAY", "HOUR", "MINUTE", "SECOND" };
	private static final long[] SECONDS = { 0, 0, 86400, 3600, 60, 1 };
	private static final BigInteger BILLION = BigInteger.valueOf(1_000_000_000L);

	private LegacyIntervalSupport() {
	}

	static boolean complete(final String text) {
		return COMPLETE.matcher(text).matches();
	}

	static BigDecimal decimal(final double value) {
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException("Interval components must be finite");
		}
		return BigDecimal.valueOf(value);
	}

	static Duration duration(final BigDecimal seconds) {
		final BigInteger[] parts = seconds.movePointRight(9).toBigIntegerExact().divideAndRemainder(BILLION);
		return Duration.ofSeconds(parts[0].longValueExact(), parts[1].longValueExact());
	}

	static IntervalValue components(final int years, final int months, final int days, final int hours,
			final int minutes, final BigDecimal seconds, final long nanos) {
		int sign = 0;
		for (int next : new int[] { Integer.signum(years), Integer.signum(months), Integer.signum(days),
				Integer.signum(hours), Integer.signum(minutes), seconds.signum(), Long.signum(nanos) }) {
			if (next != 0) {
				if (sign != 0 && sign != next) {
					throw new IllegalArgumentException("Legacy Interval requires one sign; use IntervalValue for mixed signs");
				}
				sign = next;
			}
		}
		return new IntervalValue(Math.addExact(Math.multiplyExact((long) years, 12), months), days,
				duration(seconds).plusHours(hours).plusMinutes(minutes).plusNanos(nanos));
	}

	/** Returns null for syntactically valid unqualified text used by typed parsers. */
	static IntervalValue qualified(final String text) {
		final Matcher match = SQL.matcher(text);
		if (!match.matches()) {
			throw invalid(text);
		}
		if (match.group(3) == null) {
			return null;
		}
		if (match.group(4) != null && new BigInteger(match.group(4)).signum() <= 0) {
			throw invalid(text);
		}
		final String first = match.group(3).toUpperCase(Locale.ROOT);
		final String last = match.group(6) == null ? first : match.group(6).toUpperCase(Locale.ROOT);
		final String payload = match.group(1) == null ? match.group(2) : match.group(1);
		final IntervalValue result = parse(payload, first, last, false);
		if (match.group(4) != null) {
			final Matcher leading = Pattern.compile("[+-]?([0-9]+)").matcher(payload.trim());
			if (!leading.lookingAt() || BigInteger.valueOf(new BigInteger(leading.group(1)).toString().length())
					.compareTo(new BigInteger(match.group(4))) > 0) {
				throw invalid(text);
			}
		}
		if (match.group(5) != null) {
			if (!first.equals("SECOND") || !last.equals("SECOND")) {
				throw invalid(text);
			}
			result.requireFractionalPrecision(precision(match.group(5), text));
		}
		if (match.group(7) != null) {
			if (!last.equals("SECOND")) {
				throw invalid(text);
			}
			result.requireFractionalPrecision(precision(match.group(7), text));
		}
		return result;
	}

	private static int precision(final String value, final String input) {
		final int precision = Integer.parseInt(value);
		if (precision < 0 || precision > 9) {
			throw invalid(input);
		}
		return precision;
	}

	static IntervalValue parse(final String input, final String firstUnit, final String lastUnit) {
		return parse(input, firstUnit, lastUnit, true);
	}

	private static IntervalValue parse(final String input, final String firstUnit, final String lastUnit,
			final boolean allowLowerFields) {
		String text = input.trim();
		if (text.startsWith("'") && text.endsWith("'") && text.length() >= 2) {
			text = text.substring(1, text.length() - 1).trim();
		}
		final boolean negative = text.startsWith("-");
		if (negative || text.startsWith("+")) {
			text = text.substring(1);
		}
		final int first = unit(firstUnit);
		final int last = lastUnit == null ? first : unit(lastUnit);
		if (first < 0 || last < first || text.isEmpty()) {
			throw invalid(input);
		}
		final BigDecimal[] values = new BigDecimal[6];
		java.util.Arrays.fill(values, BigDecimal.ZERO);
		if (first > 2) {
			final String[] fields = text.split(":", -1);
			// Typed legacy parsers accept lower-order fields and project them later.
			fill(values, fields, first, allowLowerFields ? Math.max(last, first + fields.length - 1) : last, input);
		} else if (last <= 2) {
			final String[] dates = text.split("[-\\s]+", -1);
			fill(values, dates, last - dates.length + 1, last, input);
			if (last - dates.length + 1 < first) {
				throw invalid(input);
			}
		} else {
			final String[] parts = text.split("\\s+");
			final String dateText = parts.length == 1 ? parts[0]
					: String.join(" ", java.util.Arrays.copyOf(parts, parts.length - 1));
			final String[] dates = dateText.split("[-\\s]+", -1);
			fill(values, dates, 2 - dates.length + 1, 2, input);
			if (2 - dates.length + 1 < first) {
				throw invalid(input);
			}
			if (parts.length > 1) {
				final String[] time = parts[parts.length - 1].split(":", -1);
				fill(values, time, 3, allowLowerFields ? Math.max(last, 3 + time.length - 1) : last, input);
			}
		}
		final long months = values[0].multiply(BigDecimal.valueOf(12)).add(values[1]).longValueExact();
		BigDecimal seconds = BigDecimal.ZERO;
		for (int index = 2; index < 6; index++) {
			seconds = seconds.add(values[index].multiply(BigDecimal.valueOf(SECONDS[index])));
		}
		final IntervalValue result = new IntervalValue(months, 0, duration(seconds));
		return negative ? result.negated() : result;
	}

	private static void fill(final BigDecimal[] values, final String[] fields, final int first, final int last,
			final String input) {
		if (first < 0 || last > 5 || fields.length > last - first + 1) {
			throw invalid(input);
		}
		for (int index = 0; index < fields.length; index++) {
			if (!NUMBER.matcher(fields[index]).matches()) {
				throw invalid(input);
			}
			if (index < fields.length - 1 && fields[index].contains(".")) {
				throw invalid(input);
			}
			values[first + index] = new BigDecimal(fields[index]);
		}
	}

	private static int unit(final String text) {
		for (int index = 0; index < UNITS.length; index++) {
			if (UNITS[index].equalsIgnoreCase(text)) {
				return index;
			}
		}
		return -1;
	}

	private static IllegalArgumentException invalid(final String input) {
		return new IllegalArgumentException("Invalid legacy interval: " + input);
	}
}
