/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.duckdb;

import java.math.BigInteger;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;
import com.sqlapp.data.converter.IntervalValueConverter;
import com.sqlapp.data.interval.IntervalValue;

/** DuckDB's independent int32 months/days and int64 microseconds, without floating point. */
public final class DuckDbIntervalCodec {
	private static final BigInteger BILLION = BigInteger.valueOf(1_000_000_000L);
	private static final Pattern PART = Pattern.compile(
			"\\G\\s*(?:([+-]?[0-9]+)\\s+(years?|months?|mons?|days?|microseconds?)\\b"
			+ "|([+-]?)([0-9]+):([0-9]{2}):([0-9]{2})(?:\\.([0-9]{1,9}))?)", Pattern.CASE_INSENSITIVE);
	private DuckDbIntervalCodec() {}
	public static IntervalValue decode(Object input) {
		if (!(input instanceof String text)) return new IntervalValueConverter().convertObject(input);
		if (text.startsWith("P") || text.startsWith("p")) return IntervalValue.parse(text);
		var matcher = PART.matcher(text);
		BigInteger months = BigInteger.ZERO, days = BigInteger.ZERO, nanos = BigInteger.ZERO;
		int position = 0;
		while (position < text.length()) {
			if (!matcher.find(position) || matcher.start() != position)
				throw new IllegalArgumentException("Invalid DuckDB interval text");
			if (matcher.group(1) != null) {
				var value = new BigInteger(matcher.group(1));
				String unit = matcher.group(2).toLowerCase(Locale.ROOT);
				if (unit.startsWith("year")) months = months.add(value.multiply(BigInteger.valueOf(12)));
				else if (unit.startsWith("mon")) months = months.add(value);
				else if (unit.startsWith("day")) days = days.add(value);
				else nanos = nanos.add(value.multiply(BigInteger.valueOf(1000)));
			} else {
				int minute = Integer.parseInt(matcher.group(5)), second = Integer.parseInt(matcher.group(6));
				if (minute >= 60 || second >= 60) throw new IllegalArgumentException("Invalid DuckDB interval time");
				var value = new BigInteger(matcher.group(4)).multiply(BigInteger.valueOf(3600))
						.add(BigInteger.valueOf(minute * 60L + second)).multiply(BILLION);
				String fraction = matcher.group(7);
				if (fraction != null) value = value.add(new BigInteger((fraction + "000000000").substring(0, 9)));
				nanos = nanos.add("-".equals(matcher.group(3)) ? value.negate() : value);
			}
			position = matcher.end();
		}
		if (position == 0) throw new IllegalArgumentException("Empty DuckDB interval");
		var parts = nanos.divideAndRemainder(BILLION);
		return new IntervalValue(months.longValueExact(), days.longValueExact(),
				Duration.ofSeconds(parts[0].longValueExact(), parts[1].longValueExact()));
	}
	public static String encode(Object input) {
		var value = decode(input);
		Math.toIntExact(value.months());
		Math.toIntExact(value.days());
		value.requireFractionalPrecision(6);
		long micros = BigInteger.valueOf(value.timePart().getSeconds()).multiply(BigInteger.valueOf(1_000_000))
				.add(BigInteger.valueOf(value.timePart().getNano() / 1000)).longValueExact();
		return value.months() + " months " + value.days() + " days " + micros + " microseconds";
	}
}
