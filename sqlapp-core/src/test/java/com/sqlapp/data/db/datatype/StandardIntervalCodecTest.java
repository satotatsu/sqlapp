/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.datatype;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.sqlapp.data.interval.IntervalValue;

class StandardIntervalCodecTest {
	static Stream<DataType> qualifiers() {
		return Stream.of(DataType.values()).filter(type -> type.getMetaTtype() == MetaType.INTERVAL
				&& type != DataType.INTERVAL && type != DataType.INTERVAL_YEAR_TO_DAY);
	}

	@ParameterizedTest
	@MethodSource("qualifiers")
	void allStandardQualifiersRoundTripWithoutRounding(DataType type) {
		var codec = new StandardIntervalCodec(type, 18, 9);
		// A common multiple of every leading/trailing unit.
		var value = type.name().contains("YEAR") || type == DataType.INTERVAL_MONTH
				? new IntervalValue(-1200, 0, Duration.ZERO)
				: new IntervalValue(0, 0, Duration.ofDays(-100));
		var converted = codec.convert(value);
		assertEquals(value, codec.parse(converted.text()));
		assertEquals(value, codec.parse(codec.literal(value)));
		assertTrue(converted.warnings().isEmpty());
		var metadata = codec.columnTypeMatcher().match("INTERVAL " + codec.qualifier()).orElseThrow();
		assertEquals(18L, metadata.getLength().orElseThrow());
		if (type.name().endsWith("SECOND")) assertEquals(9, metadata.getScale().orElseThrow());
	}

	@Test
	void calendarDayNormalizationReturnsWarningOutsideValue() {
		var codec = new StandardIntervalCodec(DataType.INTERVAL_DAY_TO_SECOND, 9, 9);
		var result = codec.convert(IntervalValue.parse("P1DT-3600.000000001S"));
		assertEquals(IntervalValue.parse("PT82799.999999999S"), result.value());
		assertEquals("+0 22:59:59.999999999", result.text());
		assertEquals(List.of(StandardIntervalCodec.Warning.CALENDAR_DAYS_AS_24_HOURS), result.warnings());
		assertThrows(UnsupportedOperationException.class, () -> result.warnings().clear());
		assertTrue(codec.convert(IntervalValue.parse("PT24H")).warnings().isEmpty());
		assertEquals(IntervalValue.ZERO, codec.convert(IntervalValue.parse("P1DT-24H")).value());
		assertEquals(1, codec.convert(IntervalValue.parse("P1DT-24H")).warnings().size());
	}

	@Test
	void monthsNeverBecomeDaysAndDayTimeNeverBecomesMonths() {
		var ym = new StandardIntervalCodec(DataType.INTERVAL_YEAR_TO_MONTH, 9, 9);
		var ds = new StandardIntervalCodec(DataType.INTERVAL_DAY_TO_SECOND, 9, 9);
		assertThrows(IllegalArgumentException.class, () -> ym.convert(IntervalValue.parse("P1M1D")));
		assertThrows(IllegalArgumentException.class, () -> ds.convert(IntervalValue.parse("P1M")));
		assertThrows(IllegalArgumentException.class, () -> ym.convert(Duration.ofDays(30)));
		assertThrows(ArithmeticException.class, () -> new StandardIntervalCodec(DataType.INTERVAL_YEAR, 9, 9)
				.convert(IntervalValue.parse("P13M")));
		assertThrows(ArithmeticException.class, () -> new StandardIntervalCodec(DataType.INTERVAL_DAY, 9, 9)
				.convert(Duration.ofHours(25)));
	}

	@Test
	void leadingAndFractionalPrecisionAreExact() {
		var ym = new StandardIntervalCodec(DataType.INTERVAL_YEAR_TO_MONTH, 9, 9);
		assertEquals("+999999999-11", ym.convert(IntervalValue.parse("P999999999Y11M")).text());
		assertThrows(ArithmeticException.class, () -> ym.convert(IntervalValue.parse("P1000000000Y")));
		var ds = new StandardIntervalCodec(DataType.INTERVAL_DAY_TO_SECOND, 9, 6);
		assertEquals("+999999999 23:59:59.999999",
				ds.convert(IntervalValue.parse("P999999999DT23H59M59.999999S")).text());
		assertThrows(ArithmeticException.class, () -> ds.convert(IntervalValue.parse("P1000000000D")));
		assertThrows(ArithmeticException.class, () -> ds.convert(IntervalValue.parse("PT0.000000001S")));
		assertEquals("+0-11", new StandardIntervalCodec(DataType.INTERVAL_YEAR_TO_MONTH, 0, 0)
				.convert(IntervalValue.parse("P11M")).text());
		assertThrows(ArithmeticException.class, () -> new StandardIntervalCodec(DataType.INTERVAL_YEAR_TO_MONTH, 0, 0)
				.convert(IntervalValue.parse("P12M")));
	}

	@Test
	void signedLongAndDurationBoundsAndCancellation() {
		var ym = new StandardIntervalCodec(DataType.INTERVAL_YEAR_TO_MONTH, 18, 9);
		for (long months : new long[] { Long.MIN_VALUE, Long.MAX_VALUE }) {
			var value = new IntervalValue(months, 0, Duration.ZERO);
			assertEquals(value, ym.parse(ym.convert(value).text()));
		}
		var ds = new StandardIntervalCodec(DataType.INTERVAL_DAY_TO_SECOND, 18, 9);
		for (Duration time : new Duration[] { Duration.ofSeconds(Long.MIN_VALUE),
				Duration.ofSeconds(Long.MAX_VALUE, 999999999) }) {
			var value = new IntervalValue(0, 0, time);
			assertEquals(value, ds.parse(ds.convert(value).text()));
		}
		assertThrows(ArithmeticException.class, () -> ds.convert(new IntervalValue(0, Long.MAX_VALUE, Duration.ZERO)));
		assertThrows(ArithmeticException.class, () -> ds.convert(new IntervalValue(0, 1,
				Duration.ofSeconds(Long.MAX_VALUE))));
		assertThrows(ArithmeticException.class, () -> ds.parse("+999999999999999999 00:00:00"));
		// Compute the final sum exactly, without a spurious intermediate overflow.
		assertEquals(IntervalValue.parse("PT1S"), ds.convert(new IntervalValue(0, 106751991167300L,
				Duration.ofSeconds(-9223372036854719999L))).value());
	}

	@Test
	void invalidTextIsRejectedAndFormattingDoesNotDependOnLocale() {
		var codec = new StandardIntervalCodec(DataType.INTERVAL_DAY_TO_SECOND, 9, 9);
		for (String text : new String[] { "1 24:00:00", "1 00:60:00", "1 00:00:60", "1 00:00:5.",
				"1 00:00:1.1234567890", "1 00:00:01 xyz", "1 -1:00:00", "", "INTERVAL '1-2' YEAR TO MONTH" })
			assertThrows(IllegalArgumentException.class, () -> codec.parse(text), text);
		Locale previous = Locale.getDefault();
		try {
			Locale.setDefault(Locale.forLanguageTag("tr-TR"));
			assertEquals(IntervalValue.parse("PT1.123456789S"),
					codec.parse("interval '+0 00:00:01.123456789' day(9) to second(9)"));
			assertEquals("+0 00:00:01.123456789", codec.convert(Duration.ofSeconds(1, 123456789)).text());
			assertEquals(IntervalValue.ZERO, codec.parse("-0 00:00:00.000"));
			assertEquals("+0 00:00:00", codec.convert(IntervalValue.ZERO).text());
		} finally { Locale.setDefault(previous); }
	}
}
