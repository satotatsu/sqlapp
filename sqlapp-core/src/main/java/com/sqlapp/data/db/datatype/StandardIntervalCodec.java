/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.datatype;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import com.sqlapp.data.converter.IntervalValueConverter;
import com.sqlapp.data.db.datatype.util.ColumnTypeMatcher;
import com.sqlapp.data.db.datatype.util.RegexColumnTypeMatcher;
import com.sqlapp.data.interval.IntervalValue;

/**
 * Exact SQL standard interval conversion, shared by dialects with fixed 24-hour
 * days. No month-to-day conversion, rounding or field truncation is permitted.
 */
public final class StandardIntervalCodec {
	private static final BigInteger BILLION = BigInteger.valueOf(1_000_000_000L);
	private static final BigInteger[] UNITS = { BigInteger.valueOf(12), BigInteger.ONE,
			BigInteger.valueOf(86400).multiply(BILLION), BigInteger.valueOf(3600).multiply(BILLION),
			BigInteger.valueOf(60).multiply(BILLION), BILLION };
	private static final String[] NAMES = { "YEAR", "MONTH", "DAY", "HOUR", "MINUTE", "SECOND" };
	private static final Pattern LITERAL = Pattern.compile(
			"INTERVAL\\s+([+-]?)'([^']*)'\\s+(.+)", Pattern.CASE_INSENSITIVE);
	private final DataType type;
	private final int first;
	private final int last;
	private final int leadingPrecision;
	private final int fractionalPrecision;

	/** Information for migration reports; it is not part of IntervalValue identity. */
	public enum Warning { CALENDAR_DAYS_AS_24_HOURS }

	/** Validated destination value, exact payload, and immutable conversion warnings. */
	public record Result(IntervalValue value, String text, List<Warning> warnings) {
		public Result {
			Objects.requireNonNull(value, "value");
			Objects.requireNonNull(text, "text");
			warnings = List.copyOf(warnings);
		}
	}

	/**
	 * @param leadingPrecision maximum digits in the leading field (0..18);
	 * dialects must additionally enforce their own supported precision range
	 * @param fractionalPrecision maximum fractional second digits (0..9)
	 */
	public StandardIntervalCodec(DataType type, int leadingPrecision, int fractionalPrecision) {
		this.type = Objects.requireNonNull(type, "type");
		String qualifier = type.name().replaceFirst("^INTERVAL_", "");
		String[] fields = qualifier.split("_TO_", -1);
		first = position(fields[0]);
		last = position(fields[fields.length - 1]);
		if (first < 0 || last < first || (first < 2 && last >= 2) || fields.length > 2)
			throw new IllegalArgumentException("Unsupported standard interval qualifier: " + type);
		if (leadingPrecision < 0 || leadingPrecision > 18)
			throw new IllegalArgumentException("Leading interval precision must be 0..18");
		if (fractionalPrecision < 0 || fractionalPrecision > 9)
			throw new IllegalArgumentException("Fractional second precision must be 0..9");
		this.leadingPrecision = leadingPrecision;
		this.fractionalPrecision = fractionalPrecision;
	}

	private static int position(String name) {
		for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equals(name)) return i;
		return -1;
	}

	public DataType dataType() { return type; }

	/** Matches SQL type declarations with independently positioned precisions. */
	public ColumnTypeMatcher columnTypeMatcher() {
		String precision = "(?:\\s*\\(\\s*(?<length>[0-9]+)"
				+ (first == 5 ? "(?:\\s*,\\s*(?<scale>[0-9]+))?" : "") + "\\s*\\))?";
		String expression = "INTERVAL\\s+" + NAMES[first] + precision;
		if (first != last) {
			expression += "\\s+TO\\s+" + NAMES[last];
			if (last == 5) expression += "(?:\\s*\\(\\s*(?<scale>[0-9]+)\\s*\\))?";
		}
		return new RegexColumnTypeMatcher(expression, (matcher, info) -> {
			info.setDataTypeName("INTERVAL " + type.name().substring("INTERVAL_".length()).replace('_', ' '));
			String length = matcher.group("length");
			info.setLength(length == null ? leadingPrecision : length);
			if (last == 5) {
				String scale = matcher.group("scale");
				info.setScale(scale == null ? fractionalPrecision : scale);
			}
		});
	}

	/** Converts canonical, legacy, Period or Duration values, or destination SQL text. */
	public Result convert(Object input) {
		Objects.requireNonNull(input, "input");
		IntervalValue value;
		if (input instanceof String text && !text.startsWith("P") && !text.startsWith("p"))
			value = parse(text);
		else value = new IntervalValueConverter().convertObject(input);
		Objects.requireNonNull(value, "interval");
		BigInteger amount;
		List<Warning> warnings = new ArrayList<>();
		if (first < 2) {
			if (value.days() != 0 || !value.timePart().isZero())
				throw new IllegalArgumentException("Year-month interval cannot contain days or time");
			amount = BigInteger.valueOf(value.months());
		} else {
			if (value.months() != 0)
				throw new IllegalArgumentException("Day-time interval cannot contain months");
			amount = BigInteger.valueOf(value.days()).multiply(UNITS[2])
					.add(BigInteger.valueOf(value.timePart().getSeconds()).multiply(BILLION))
					.add(BigInteger.valueOf(value.timePart().getNano()));
			if (value.days() != 0) warnings.add(Warning.CALENDAR_DAYS_AS_24_HOURS);
			BigInteger[] parts = amount.divideAndRemainder(BILLION);
			// Check the complete common model range, even if the database permits more.
			value = new IntervalValue(0, 0,
					Duration.ofSeconds(parts[0].longValueExact(), parts[1].longValueExact()));
		}
		BigInteger magnitude = amount.abs();
		BigInteger minimum = last == 5 ? BigInteger.TEN.pow(9 - fractionalPrecision) : UNITS[last];
		if (magnitude.remainder(minimum).signum() != 0)
			throw new ArithmeticException("Interval loses precision for " + type + " (fractional precision "
					+ fractionalPrecision + ")");
		if (magnitude.divide(UNITS[first]).compareTo(BigInteger.TEN.pow(leadingPrecision)) >= 0)
			throw new ArithmeticException("Interval leading field exceeds precision " + leadingPrecision);
		StringBuilder text = new StringBuilder(amount.signum() < 0 ? "-" : "+");
		for (int i = first; i <= last; i++) {
			if (i > first) text.append(i == 1 ? "-" : i == 3 ? " " : ":");
			BigInteger[] division = magnitude.divideAndRemainder(UNITS[i]);
			String digits = division[0].toString();
			if (i > first && i != 1 && digits.length() < 2) text.append('0');
			text.append(digits);
			magnitude = division[1];
			if (i == 5 && magnitude.signum() != 0) {
				String fraction = String.format(Locale.ROOT, "%09d", magnitude.longValueExact());
				text.append('.').append(fraction.replaceFirst("0+$", ""));
			}
		}
		return new Result(value, text.toString(), warnings);
	}

	/**
	 * Parses an exact destination payload or full INTERVAL literal. Day-time
	 * values are fixed durations; reading them does not invent calendar days.
	 */
	public IntervalValue parse(String input) {
		Objects.requireNonNull(input, "input");
		String text = input.trim();
		var literal = LITERAL.matcher(text);
		if (literal.matches()) {
			String qualifier = literal.group(3).toUpperCase(Locale.ROOT)
					.replaceAll("\\([0-9]+(?:\\s*,\\s*[0-9]+)?\\)", "")
					.replaceAll("\\s+", "_");
			if (!type.name().equals("INTERVAL_" + qualifier))
				throw new IllegalArgumentException("Interval qualifier does not match " + type);
			text = literal.group(1) + literal.group(2);
		}
		int sign = 1;
		if (text.startsWith("+") || text.startsWith("-")) {
			if (text.charAt(0) == '-') sign = -1;
			text = text.substring(1);
		}
		String[] components = text.split(first < 2 ? "-" : "[ :]", -1);
		if (components.length != last - first + 1)
			throw new IllegalArgumentException("Invalid " + type + " payload: " + input);
		BigInteger amount = BigInteger.ZERO;
		for (int i = first; i <= last; i++) {
			String component = components[i - first];
			if (!component.matches(i == 5 ? "[0-9]+(?:\\.[0-9]{1,9})?" : "[0-9]+"))
				throw new IllegalArgumentException("Invalid interval field: " + component);
			BigDecimal number = new BigDecimal(component);
			int bound = i == 1 ? 12 : i == 3 ? 24 : 60;
			if (i > first && number.compareTo(BigDecimal.valueOf(bound)) >= 0)
				throw new IllegalArgumentException("Interval field out of range: " + component);
			amount = amount.add(number.multiply(new BigDecimal(UNITS[i])).toBigIntegerExact());
		}
		if (sign < 0) amount = amount.negate();
		if (first < 2) return new IntervalValue(amount.longValueExact(), 0, Duration.ZERO);
		BigInteger[] parts = amount.divideAndRemainder(BILLION);
		return new IntervalValue(0, 0, Duration.ofSeconds(parts[0].longValueExact(), parts[1].longValueExact()));
	}

	/** Destination qualifier including explicit precision, suitable for a SQL literal or CAST. */
	public String qualifier() {
		String start = NAMES[first] + "(" + leadingPrecision
				+ (first == 5 ? "," + fractionalPrecision : "") + ")";
		if (first == last) return start;
		return start + " TO " + NAMES[last] + (last == 5 ? "(" + fractionalPrecision + ")" : "");
	}

	public String literal(Object value) {
		return "INTERVAL '" + convert(value).text() + "' " + qualifier();
	}
}
