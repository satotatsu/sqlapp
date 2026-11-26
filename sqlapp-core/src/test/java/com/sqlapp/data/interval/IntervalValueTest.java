/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.interval;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.sqlapp.util.file.CsvParser;
import com.sqlapp.util.file.CsvWriter;

class IntervalValueTest {
	@ParameterizedTest
	@CsvSource({ "P1Y,P12M", "P1Y2M-3DT4H5M6.123456789S,P14M-3DT14706.123456789S", "P1D,P1D", "PT24H,PT86400S",
			"PT25H,PT90000S", "P1DT-1H,P1DT-3600S", "PT-0.000000001S,PT-0.000000001S", "PT-1.5S,PT-1.5S",
			"PT1H-60M,PT0S", "P1Y-12M,PT0S", "P+2M-3DT+4.120000000S,P2M-3DT4.12S", "p1dt2h,P1DT7200S", "PT0S,PT0S",
			"P-0D,PT0S", "PT-0.000000000S,PT0S", "P-0Y+0M-0DT-0H+0M-0S,PT0S" })
	void parsesAndEmitsCanonicalText(final String input, final String canonical) {
		final IntervalValue value = IntervalValue.parse(input);
		assertEquals(canonical, value.toString());
		assertEquals(value, IntervalValue.parse(value.toString()));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "P", "PT", "P1DT", "P1M1Y", "P1D1D", "PT1S1H", "P1W", "P1.5D", "P1.5Y", "P1.5M",
			"PT1.5H", "PT1.5M", "PT.5S", "PT1.S", "PT1,5S", "PT1.1234567890S", " P1D", "P1D ", "-P1D", "P1DT2",
			"PTNaNS", "PTInfinityS", "P１D", "INTERVAL '1' DAY", "1-2-3 4:5:6", "PT1S\n", "P1DT1Sgarbage" })
	void rejectsInvalidSyntaxWithoutFallback(final String input) {
		assertThrows(DateTimeParseException.class, () -> IntervalValue.parse(input));
	}

	@Test
	void keepsCalendarDaysSeparateFromTime() {
		final IntervalValue day = IntervalValue.parse("P1D");
		final IntervalValue hours = IntervalValue.parse("PT24H");
		assertNotEquals(day, hours);
		assertEquals(1, day.days());
		assertEquals(Duration.ZERO, day.timePart());
		assertEquals(0, hours.days());
		assertEquals(Duration.ofHours(24), hours.timePart());
		assertEquals(new IntervalValue(1, -1, Duration.ofHours(25)), IntervalValue.parse("P1M-1DT25H"));
	}

	@Test
	void equalityAndHashCodeUseAllThreeComponentsExactly() {
		final IntervalValue a = IntervalValue.parse("P1Y");
		final IntervalValue b = IntervalValue.parse("P12M");
		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
		assertNotEquals(IntervalValue.parse("PT0.000000001S"), IntervalValue.parse("PT0.000000002S"));
		assertFalse(Comparable.class.isAssignableFrom(IntervalValue.class));
	}

	@Test
	void retainsLongAndDurationExtremesThroughText() {
		for (long months : new long[] { Long.MIN_VALUE, -1, 0, 1, Long.MAX_VALUE }) {
			for (long days : new long[] { Long.MIN_VALUE, Long.MAX_VALUE }) {
				for (Duration time : new Duration[] { Duration.ofSeconds(Long.MIN_VALUE),
						Duration.ofSeconds(Long.MIN_VALUE, 1), Duration.ofNanos(-1),
						Duration.ofSeconds(Long.MAX_VALUE, 999_999_999) }) {
					final IntervalValue value = new IntervalValue(months, days, time);
					assertEquals(value, IntervalValue.parse(value.toString()));
				}
			}
		}
	}

	@Test
	void randomMixedSignsRoundTripWithoutDayTimeNormalization() {
		final Random random = new Random(20261117);
		for (int index = 0; index < 1000; index++) {
			final IntervalValue value = new IntervalValue(random.nextLong(), random.nextLong(),
					Duration.ofSeconds(random.nextLong(), random.nextInt(1_000_000_000)));
			assertEquals(value, IntervalValue.parse(value.toString()));
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "P9223372036854775808M", "P-9223372036854775809M", "P9223372036854775808D",
			"P-9223372036854775809D", "P9223372036854775807Y", "PT9223372036854775808S",
			"PT-9223372036854775808.000000001S", "PT9223372036854775807H" })
	void rejectsOutOfRangeParsedValues(final String input) {
		assertThrows(ArithmeticException.class, () -> IntervalValue.parse(input));
	}

	@Test
	void combinesInputComponentsExactlyBeforeCheckingTheirFinalRange() {
		assertEquals(IntervalValue.ZERO, IntervalValue.parse("P9223372036854775807Y-110680464442257309684M"));
		assertEquals(IntervalValue.ZERO, IntervalValue.parse("PT9223372036854775807H-33204139332677192905200S"));
	}

	@Test
	void arithmeticIsImmutableAndDoesNotCarryDaysIntoTime() {
		final IntervalValue a = IntervalValue.parse("P2M-1DT25H");
		final IntervalValue b = IntervalValue.parse("P-1M2DT-1H");
		assertEquals(IntervalValue.parse("P1M1DT24H"), a.plus(b));
		assertEquals(a, a.plus(b).minus(b));
		assertEquals(IntervalValue.parse("P-4M2DT-50H"), a.multipliedBy(-2));
		assertEquals(a, a.negated().negated());
		assertEquals(IntervalValue.ZERO, a.multipliedBy(0));
		assertEquals(IntervalValue.parse("P2M-1DT25H"), a);
	}

	@Test
	void arithmeticOverflowIsNeverRoundedOrWrapped() {
		for (IntervalValue[] pair : new IntervalValue[][] {
				{ new IntervalValue(Long.MAX_VALUE, 0, Duration.ZERO), IntervalValue.parse("P1M") },
				{ new IntervalValue(0, Long.MAX_VALUE, Duration.ZERO), IntervalValue.parse("P1D") },
				{ new IntervalValue(0, 0, Duration.ofSeconds(Long.MAX_VALUE, 999_999_999)),
						IntervalValue.parse("PT0.000000001S") },
				{ new IntervalValue(Long.MIN_VALUE, 0, Duration.ZERO), IntervalValue.parse("P-1M") },
				{ new IntervalValue(0, Long.MIN_VALUE, Duration.ZERO), IntervalValue.parse("P-1D") },
				{ new IntervalValue(0, 0, Duration.ofSeconds(Long.MIN_VALUE)),
						IntervalValue.parse("PT-0.000000001S") } }) {
			assertThrows(ArithmeticException.class, () -> pair[0].plus(pair[1]));
			assertThrows(ArithmeticException.class, () -> pair[0].minus(pair[1].negated()));
			assertThrows(ArithmeticException.class, () -> pair[0].multipliedBy(2));
		}
		assertThrows(ArithmeticException.class, () -> new IntervalValue(Long.MIN_VALUE, 0, Duration.ZERO).negated());
		assertThrows(ArithmeticException.class, () -> new IntervalValue(0, Long.MIN_VALUE, Duration.ZERO).negated());
		assertThrows(ArithmeticException.class,
				() -> new IntervalValue(0, 0, Duration.ofSeconds(Long.MIN_VALUE)).negated());
	}

	@Test
	void fractionalPrecisionValidationRejectsLossIncludingNegativeValues() {
		for (String text : new String[] { "PT1.123456S", "PT-0.000001S", "PT0S" }) {
			final IntervalValue value = IntervalValue.parse(text);
			assertSame(value, value.requireFractionalPrecision(6));
		}
		for (String text : new String[] { "PT1.123456789S", "PT-0.000000001S", "PT-1.123456001S" }) {
			final IntervalValue value = IntervalValue.parse(text);
			assertSame(value, value.requireFractionalPrecision(9));
			assertThrows(ArithmeticException.class, () -> value.requireFractionalPrecision(6));
		}
		assertThrows(ArithmeticException.class, () -> IntervalValue.parse("PT0.1S").requireFractionalPrecision(0));
		assertSame(IntervalValue.ZERO, IntervalValue.ZERO.requireFractionalPrecision(0));
		assertThrows(IllegalArgumentException.class, () -> IntervalValue.ZERO.requireFractionalPrecision(-1));
		assertThrows(IllegalArgumentException.class, () -> IntervalValue.ZERO.requireFractionalPrecision(10));
	}

	@Test
	void javaTimeConversionsAreExplicitAndExact() {
		assertEquals(Period.of(1, 2, -3), IntervalValue.parse("P14M-3D").toPeriod());
		assertEquals(Period.of(-1, -2, 3), IntervalValue.parse("P-14M3D").toPeriod());
		assertEquals(Duration.ofHours(25), IntervalValue.parse("PT25H").toDuration());
		assertThrows(DateTimeException.class, () -> IntervalValue.parse("P1D").toDuration());
		assertThrows(DateTimeException.class, () -> IntervalValue.parse("P1M").toDuration());
		assertThrows(DateTimeException.class, () -> IntervalValue.parse("PT1S").toPeriod());
		assertThrows(ArithmeticException.class, () -> new IntervalValue(Long.MAX_VALUE, 0, Duration.ZERO).toPeriod());
		assertThrows(ArithmeticException.class, () -> new IntervalValue(0, Long.MIN_VALUE, Duration.ZERO).toPeriod());
	}

	@Test
	@ResourceLock(Resources.LOCALE)
	void parsingAndFormattingDoNotDependOnLocale() {
		final Locale original = Locale.getDefault();
		try {
			for (Locale locale : new Locale[] { Locale.forLanguageTag("tr-TR"), Locale.GERMANY, Locale.JAPAN }) {
				Locale.setDefault(locale);
				assertEquals("P14M-3DT14706.123456789S", IntervalValue.parse("p1y2m-3dt4h5m6.123456789s").toString());
			}
		} finally {
			Locale.setDefault(original);
		}
	}

	@Test
	void canonicalTextSurvivesCsvTransport() throws Exception {
		final IntervalValue[] values = { IntervalValue.ZERO, IntervalValue.parse("P1D"), IntervalValue.parse("PT24H"),
				IntervalValue.parse("P14M-3DT-0.123456789S"),
				new IntervalValue(Long.MIN_VALUE, Long.MAX_VALUE, Duration.ofSeconds(Long.MIN_VALUE, 1)) };
		final var text = new StringWriter();
		try (var writer = new CsvWriter(text, settings -> {
		})) {
			for (IntervalValue value : values) {
				writer.writeRow(value.toString());
			}
		}
		final int[] index = { 0 };
		try (var parser = new CsvParser(new StringReader(text.toString()), settings -> {
		})) {
			parser.readAll((row, number) -> assertEquals(values[index[0]++], IntervalValue.parse(row[0])));
		}
		assertEquals(values.length, index[0]);
	}

	@Test
	void serializesNewValueWithoutInvolvingLegacyClasses() throws Exception {
		final IntervalValue original = IntervalValue.parse("P14M-3DT-0.123456789S");
		final var bytes = new ByteArrayOutputStream();
		try (var output = new ObjectOutputStream(bytes)) {
			output.writeObject(original);
		}
		try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
			assertEquals(original, input.readObject());
		}
	}

	@Test
	void rejectsNullValues() {
		assertThrows(NullPointerException.class, () -> new IntervalValue(0, 0, null));
		assertThrows(NullPointerException.class, () -> IntervalValue.parse(null));
		assertThrows(NullPointerException.class, () -> IntervalValue.ZERO.plus(null));
		assertThrows(NullPointerException.class, () -> IntervalValue.ZERO.minus(null));
	}
}
