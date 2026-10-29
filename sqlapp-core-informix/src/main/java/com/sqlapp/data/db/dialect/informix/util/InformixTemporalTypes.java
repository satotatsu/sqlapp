/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.util;

import java.util.Locale;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Native DATETIME/INTERVAL declarations and JDBC's encoded INTERVAL column
 * size.
 */
public final class InformixTemporalTypes {
	private static final List<String> UNITS = List.of("YEAR", "MONTH", "DAY", "HOUR", "MINUTE", "SECOND", "FRACTION");
	private static final Pattern DECLARATION = Pattern.compile(
			"(DATETIME|INTERVAL) (YEAR|MONTH|DAY|HOUR|MINUTE|SECOND|FRACTION)(?:\\(([1-9])\\))? TO (YEAR|MONTH|DAY|HOUR|MINUTE|SECOND|FRACTION)(?:\\(([1-5])\\))?");

	private InformixTemporalTypes() {
	}

	public static String normalize(String declaration) {
		final String text = declaration.strip().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ")
				.replaceAll("\\)\\s*TO\\b", ") TO");
		final var match = DECLARATION.matcher(text);
		if (!match.matches())
			throw new IllegalArgumentException("Invalid Informix temporal type: " + declaration);
		int first = UNITS.indexOf(match.group(2));
		int last = UNITS.indexOf(match.group(4));
		if (first > last || match.group(5) != null && last != 6
				|| match.group(3) != null && (first == 6 || "DATETIME".equals(match.group(1)))
				|| "INTERVAL".equals(match.group(1)) && (first < 2) != (last < 2))
			throw new IllegalArgumentException("Invalid Informix temporal qualifiers: " + declaration);
		return text;
	}

	public static String interval(long encoded) {
		if (encoded <= 0 || encoded > 65535)
			throw new IllegalArgumentException("Invalid Informix INTERVAL size: " + encoded);
		int digits = (int) encoded >>> 8;
		int first = (int) (encoded >>> 4) & 15;
		int last = (int) encoded & 15;
		if (first > 12 || (first & 1) != 0 || last < 11 && (last & 1) != 0)
			throw new IllegalArgumentException("Invalid Informix INTERVAL qualifier code: " + encoded);
		final String start = UNITS.get(first / 2);
		final String end = last >= 11 ? "FRACTION(" + (last - 10) + ")" : UNITS.get(last / 2);
		if (first == 12) {
			if (digits != last - 10)
				throw new IllegalArgumentException("Invalid Informix fractional INTERVAL size: " + encoded);
			return normalize("INTERVAL FRACTION TO " + end);
		}
		int precision = digits - (Math.min(last, 10) - first) - Math.max(last - 10, 0);
		if (precision < 1 || precision > 9)
			throw new IllegalArgumentException("Invalid Informix INTERVAL leading precision: " + encoded);
		return normalize("INTERVAL " + start + "(" + precision + ") TO " + end);
	}
}
