/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.db.datatype;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.TextStyle;
import java.time.temporal.ChronoField;
import java.util.Locale;

/**
 * Text formats shared by {@link TimeWithTimeZoneType} and
 * {@link TimestampWithTimeZoneType}.
 * <p>
 * Output keeps every non-zero fractional digit and the offset, for example
 * {@code 2026-11-20 12:34:56.1234 +05:30}. Input accepts an optional
 * {@code T} separator, 1 to 9 fractional digits, and an offset written as
 * {@code +05:30}, {@code +0530}, {@code +05} or {@code Z}, a region such as
 * {@code Asia/Tokyo}, or a short zone name. Text without zone information is
 * resolved by the converter's default zone (the JVM default time zone).
 * </p>
 */
final class ZonedTemporalFormats {

	private ZonedTemporalFormats() {
	}

	/** {@code uuuu-MM-dd HH:mm:ss[.fraction] +HH:MM} */
	static DateTimeFormatter dateTimeText() {
		return new DateTimeFormatterBuilder().appendPattern("uuuu-MM-dd HH:mm:ss")
				.appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).appendLiteral(' ')
				.appendOffset("+HH:MM", "+00:00").toFormatter(Locale.ROOT);
	}

	/** {@code HH:mm:ss[.fraction] +HH:MM} */
	static DateTimeFormatter timeText() {
		return new DateTimeFormatterBuilder().appendPattern("HH:mm:ss")
				.appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).appendLiteral(' ')
				.appendOffset("+HH:MM", "+00:00").toFormatter(Locale.ROOT);
	}

	/** Date with an optional time and zone. */
	static DateTimeFormatter dateTimeParser() {
		final DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder().parseCaseInsensitive()
				.appendPattern("uuuu-M-d").optionalStart();
		builder.optionalStart().appendLiteral('T').optionalEnd();
		builder.optionalStart().appendLiteral(' ').optionalEnd();
		time(builder).optionalEnd();
		return zone(builder).toFormatter(Locale.ENGLISH);
	}

	/** Time with an optional zone. */
	static DateTimeFormatter timeParser() {
		return zone(time(new DateTimeFormatterBuilder().parseCaseInsensitive())).toFormatter(Locale.ENGLISH);
	}

	private static DateTimeFormatterBuilder time(final DateTimeFormatterBuilder builder) {
		return builder.appendPattern("H:m").optionalStart().appendLiteral(':').appendPattern("s").optionalStart()
				.appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true).optionalEnd().optionalEnd();
	}

	private static DateTimeFormatterBuilder zone(final DateTimeFormatterBuilder builder) {
		// Alternatives are tried longest first; "+HH:MM:ss" alone would read
		// "+0530" as "+05".
		return builder.optionalStart().appendLiteral(' ').optionalEnd() //
				.optionalStart().appendOffset("+HH:MM:ss", "Z").optionalEnd() //
				.optionalStart().appendOffset("+HHMMss", "Z").optionalEnd() //
				.optionalStart().appendOffset("+HH", "Z").optionalEnd() //
				.optionalStart().appendZoneRegionId().optionalEnd() //
				.optionalStart().appendZoneText(TextStyle.SHORT).optionalEnd();
	}
}
