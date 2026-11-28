/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.time.Duration;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.util.PGInterval;
import org.postgresql.util.PGobject;

import com.sqlapp.data.db.dialect.postgres.converter.FromPGIntervalConverter;
import com.sqlapp.data.db.dialect.postgres.converter.ToPGIntervalConverter;
import com.sqlapp.data.interval.IntervalValue;

class PostgresIntervalCodecTest {
	@Test
	void oldServerClockAndIntMinimumDayOutputRemainExact() {
		var value = IntervalValue.parse("PT25H0.123456S");
		assertEquals(value, PostgresIntervalCodec.parse(PostgresIntervalCodec.encode(value)));
		assertTrue(PostgresIntervalCodec.encode(value).endsWith("+25:00:00.123456"));
		assertEquals(new IntervalValue(Integer.MAX_VALUE, Integer.MIN_VALUE, Duration.ZERO),
				PostgresIntervalCodec.parse("+178956970-7 --2147483648 +0:00:00"));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "1 year 2 mons 3 days 04:05:06.123456|P14M3DT14706.123456S",
			"-1 year -2 mons +3 days -04:05:06|P-14M3DT-14706S",
			"@ 1 year 2 mons -3 days 4 hours 5 mins 6 secs ago|P-14M3DT-14706S", "-1-2 +3 -4:05:06|P-14M3DT-14706S",
			"1-2|P14M", "-1-2|P-14M", "3 4:05:06|P3DT14706S", "-3 04:05:06|P-3DT-14706S", "-3 +04:05:06|P-3DT14706S",
			"1 day -01:00:00|P1DT-3600S", "25:00:00|PT90000S", "-00:00:00.000001|PT-0.000001S",
			"P-1Y-2M3DT-4H-5M-6S|P-14M3DT-14706S", "@ 0|PT0S", "00:00:00|PT0S" })
	void parsesAllServerOutputStylesExactly(final String input, final String expected) {
		final IntervalValue value = IntervalValue.parse(expected);
		assertEquals(value, PostgresIntervalCodec.parse(input));
		assertEquals(value, PostgresIntervalCodec.parse(PostgresIntervalCodec.encode(value)));
	}

	@Test
	void driverObjectsPreserveIndependentSignsAndTimeBeyondADay() {
		final PGInterval original = new PGInterval(-1, -2, 3, -25, -4, -0.000001);
		final var from = new FromPGIntervalConverter();
		final var to = new ToPGIntervalConverter();
		final IntervalValue value = from.convertObject(original);
		assertEquals(-14, value.months());
		assertEquals(3, value.days());
		assertEquals(Duration.ofHours(-25).plusMinutes(-4).minusNanos(1000), value.timePart());
		assertEquals(value, from.convertObject(to.convertObject(value)));
	}

	@Test
	void postgresFieldBoundsRoundTripWithoutDoubleOrIntHourOverflow() {
		for (long micros : new long[] { Long.MIN_VALUE, -1, 0, 1, Long.MAX_VALUE }) {
			final BigInteger[] parts = BigInteger.valueOf(micros).divideAndRemainder(BigInteger.valueOf(1_000_000));
			final IntervalValue value = new IntervalValue(Integer.MIN_VALUE, Integer.MAX_VALUE,
					Duration.ofSeconds(parts[0].longValueExact(), parts[1].longValueExact() * 1000));
			final PGobject driver = new ToPGIntervalConverter().convertObject(value);
			assertEquals(value, new FromPGIntervalConverter().convertObject(driver));
			assertEquals("interval", driver.getType());
		}
	}

	@Test
	void rejectsFieldOverflowAndPrecisionLossBeforeBuildingADriverObject() {
		final var to = new ToPGIntervalConverter();
		assertThrows(ArithmeticException.class, () -> to.convertObject(IntervalValue.parse("PT0.000000001S")));
		assertThrows(ArithmeticException.class, () -> to.convertObject("PT-0.000000001S"));
		assertThrows(ArithmeticException.class,
				() -> to.convertObject(new IntervalValue((long) Integer.MAX_VALUE + 1, 0, Duration.ZERO)));
		assertThrows(ArithmeticException.class,
				() -> to.convertObject(new IntervalValue(0, (long) Integer.MIN_VALUE - 1, Duration.ZERO)));
		assertThrows(ArithmeticException.class,
				() -> to.convertObject(new IntervalValue(0, 0, Duration.ofSeconds(Long.MAX_VALUE))));
		assertThrows(IllegalArgumentException.class, () -> PostgresIntervalCodec
				.parse("+2147483647 mons +2147483647 days +9223372036854775807 microseconds"));
		assertThrows(IllegalArgumentException.class, () -> PostgresIntervalCodec
				.parse("-2147483648 mons -2147483648 days -9223372036854775808 microseconds"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "garbage", "1 day garbage", "1 days2 hours", "01:99:00", "infinity", "-infinity",
			"NaN seconds", "0.123456789 seconds", "PT0.000000001S", "1.5 months", "1#2 days" })
	void neverFallsBackToZeroOrRoundsUnknownText(final String text) {
		assertThrows(RuntimeException.class, () -> PostgresIntervalCodec.parse(text));
	}

	@Test
	void nullAndCopyAreExplicit() throws Exception {
		final var from = new FromPGIntervalConverter();
		final var to = new ToPGIntervalConverter();
		assertNull(from.convertObject((Object) null));
		assertNull(to.convertObject((Object) null));
		final PGInterval nullDriver = new PGInterval();
		nullDriver.setValue(null);
		assertNull(from.convertObject(nullDriver));
		final PGobject wrong = new PGobject();
		wrong.setType("point");
		wrong.setValue("(1,2)");
		assertThrows(IllegalArgumentException.class, () -> from.convertObject(wrong));
		final IntervalValue value = IntervalValue.parse("P1DT-1H");
		assertSame(value, from.copy(value));
		final PGobject driver = to.convertObject(value);
		assertNotSame(driver, to.copy(driver));
		assertEquals(value, from.convertObject(to.copy(driver)));
	}
}
