/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.interval;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Timestamp;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.MonthDay;
import java.time.YearMonth;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IntervalLegacyContractTest {
	@Test
	void allNegativeConstructorComponentsShareOneSign() {
		final Interval integer = new Interval(-1, -2, -3, -4, -5, -6, -123_456_789);
		final Interval decimal = new Interval(-1, -2, -3, -4, -5, -6.123456789);
		assertEquals(integer, decimal);
		assertEquals(new IntervalValue(-14, -3, Duration.ofSeconds(-14_707, 876_543_211)), integer.toIntervalValue());
		assertFalse(new IntervalMonth(-25).isPositive());
		assertFalse(new IntervalSecond(-0.123456789).isPositive());
		assertTrue(new IntervalSecond(-0.0).isPositive());
	}

	@Test
	void constructorsRejectMixedSignsAndUnrepresentableMagnitudes() {
		assertThrows(IllegalArgumentException.class, () -> new Interval(1, -1, 0, 0, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> new IntervalSecond(-1, 1));
		assertThrows(IllegalArgumentException.class, () -> new Interval(0, 1, -1, 0, 0, 0));
		assertThrows(ArithmeticException.class, () -> new IntervalSecond(Integer.MIN_VALUE));
		assertThrows(ArithmeticException.class, () -> new IntervalDay(24_856));
		assertThrows(ArithmeticException.class, () -> new IntervalYear(Integer.MAX_VALUE));
		assertThrows(ArithmeticException.class, () -> new IntervalSecond(0, Integer.MAX_VALUE).scale(Integer.MAX_VALUE));
		assertThrows(IllegalArgumentException.class, () -> new IntervalSecond(Double.NaN));
		assertThrows(IllegalArgumentException.class, () -> new IntervalSecond(Double.POSITIVE_INFINITY));
	}

	@Test
	void settersRetainMagnitudeAndReplacementSemanticsWithoutOverriddenGetters() {
		final IntervalSecond seconds = new IntervalSecond(61);
		seconds.setDays(1);
		assertEquals(86_461, seconds.getSecondsFull());
		seconds.setNegative();
		seconds.setSeconds(-2);
		assertFalse(seconds.isPositive());
		assertEquals(86_462, seconds.getSecondsFull());
		final IntervalMinute minutes = new IntervalMinute(61);
		minutes.setHours(2);
		assertEquals(7_260, minutes.getSecondsFull());
		final Interval months = new IntervalYearToMonth(10, 1);
		months.setMonths(26);
		assertEquals(26, months.getMonthsFull());
	}

	@Test
	void failedMutationsLeaveOriginalFieldsUntouched() {
		final Interval value = new Interval(1, 2, 3, 4, 5, 6, 123_456_789);
		final Interval before = value.clone();
		assertThrows(ArithmeticException.class, () -> value.scale(Integer.MAX_VALUE));
		assertEquals(before, value);
		assertThrows(ArithmeticException.class, () -> value.setDays(Integer.MAX_VALUE));
		assertEquals(before, value);
		assertThrows(IllegalArgumentException.class, () -> value.setSeconds(Double.NaN));
		assertEquals(before, value);
		final Interval date = new IntervalMonth(1).toInterval();
		assertThrows(IllegalArgumentException.class, () -> new IntervalSecond(-1).add(date));
		assertEquals(1, date.getMonthsFull());
		assertEquals(0, date.getSecondsFull());
	}

	@Test
	void signedAdditionAndScalingUseTheArgumentAsTheMutationTarget() {
		final Interval target = new IntervalSecond(1, 200_000_000).toInterval();
		final Interval source = new IntervalSecond(-2, -500_000_000).toInterval();
		source.add(target);
		assertEquals(new IntervalSecond(-1, -300_000_000).toInterval(), target);
		assertEquals(new IntervalSecond(-2, -500_000_000).toInterval(), source);
		assertTrue(target.scale(-2).isPositive());
		assertEquals(2, target.getSecondsFull());
		assertEquals(600_000_000, target.getNanos());
		assertEquals(new Interval(), target.scale(0));
	}

	@Test
	void calendarUsesOneTotalMonthAdditionBeforeDaysAndPreservesZone() {
		final Calendar start = calendar("UTC", 2024, Calendar.FEBRUARY, 29);
		final Interval value = new Interval(1, 1, 0, 0, 0, 0);
		final Calendar result = value.add(start);
		assertEquals(2025, result.get(Calendar.YEAR));
		assertEquals(Calendar.MARCH, result.get(Calendar.MONTH));
		assertEquals(29, result.get(Calendar.DAY_OF_MONTH));
		assertEquals(start.getTimeZone(), result.getTimeZone());
		assertEquals(LocalDate.of(2025, 3, 29), value.add(LocalDate.of(2024, 2, 29)));
	}

	@Test
	void calendarDayAdditionRetainsWallClockAcrossDst() {
		final Calendar start = calendar("America/New_York", 2026, Calendar.MARCH, 7);
		start.set(Calendar.HOUR_OF_DAY, 12);
		final Calendar result = new IntervalDay(1).add(start);
		assertEquals(12, result.get(Calendar.HOUR_OF_DAY));
		assertEquals(23 * 3_600_000L, result.getTimeInMillis() - start.getTimeInMillis());
	}

	@Test
	void timestampHandlesNegativeFractionBorrowWithoutMutatingInput() {
		final Timestamp input = Timestamp.valueOf("2026-01-01 00:00:00.2");
		assertEquals(Timestamp.valueOf("2025-12-31 23:59:59.7"), new IntervalSecond(-0.5).add(input));
		assertEquals(Timestamp.valueOf("2026-01-01 00:00:00.2"), input);
	}

	@Test
	void calendarTruncatesOnlySubMillisecondPartOfSignedTime() {
		final Calendar start = calendar("UTC", 2026, Calendar.JANUARY, 1);
		assertEquals(start, new IntervalSecond(-0.000000001).add(start));
		assertEquals(start.getTimeInMillis() - 1, new IntervalSecond(-0.001000001).add(start).getTimeInMillis());
	}

	@Test
	void javaTimeOverloadsApplySignedMonthsAndNanos() {
		assertEquals(Month.DECEMBER, new IntervalMonth(-1).add(Month.JANUARY));
		assertEquals(YearMonth.of(2025, 12), new IntervalMonth(-1).add(YearMonth.of(2026, 1)));
		assertEquals(MonthDay.of(12, 31), new IntervalDay(-1).add(MonthDay.of(1, 1), 1));
		assertEquals(MonthDay.of(2, 29), new IntervalDay(1).add(MonthDay.of(2, 28), 1));
		assertEquals(LocalDateTime.of(2025, 12, 31, 23, 59, 59, 999_999_999),
				new IntervalSecond(-0.000000001).add(LocalDateTime.of(2026, 1, 1, 0, 0)));
		assertEquals(LocalTime.of(1, 0), new IntervalHour(25).add(LocalTime.MIDNIGHT));
	}

	@Test
	void yearMonthConversionRequiresAnExplicitBaseWhenAmbiguous() {
		assertEquals(YearMonth.of(5, 11), new IntervalYearToMonth(5, 11).toYearMonth());
		assertThrows(DateTimeException.class, () -> new IntervalYearToMonth(1, 0).toYearMonth());
		assertEquals(YearMonth.of(2027, 1), new IntervalYearToMonth(1, 0).toYearMonth(YearMonth.of(2026, 1)));
		assertEquals(YearMonth.of(2025, 12), new IntervalYearToMonth(0, -1).toYearMonth(YearMonth.of(2026, 1)));
	}

	@Test
	void scalarConversionsDoNotCountTotalComponentsTwice() {
		final Interval[] values = { new IntervalHour(25), new IntervalMinute(1501), new IntervalSecond(90061, 123) };
		for (Interval source : values) {
			assertEquals(source.getSecondsFull(), IntervalDayToSecond.toDayToSecondType(source).getSecondsFull());
			assertEquals(source.getSecondsFull(), IntervalMinuteToSecond.toMinuteToSecondType(source).getSecondsFull());
			assertEquals(source.getSecondsFull() / 3600, IntervalHour.toHourType(source).getHours());
		}
	}

	@Test
	void restrictedTargetsStillDropUnsupportedComponentsAndPrecision() {
		final IntervalYearToMonth months = new IntervalYearToMonth(1, 2);
		new IntervalDay(1).add(months);
		assertEquals(0, months.getSecondsFull());
		final IntervalDayToMinute minutes = new IntervalDayToMinute();
		new IntervalSecond(61, 999).add(minutes);
		assertEquals(60, minutes.getSecondsFull());
		assertEquals(0, minutes.getNanos());
		final IntervalYearToDay days = new IntervalYearToDay();
		days.setNanos(1);
		assertEquals(0, days.getNanos());
		days.setNanos(86_400_000_000_000L);
		assertEquals(1, days.getDays());
	}

	@ParameterizedTest
	@ValueSource(strings = { "INTERVAL '1' DAY xyz!!", "INTERVAL '1#2' DAY", "INTERVAL '1' DAY TO YEAR",
			"INTERVAL '1::2' HOUR TO SECOND", "INTERVAL '1.0000000001' SECOND", "INTERVAL '1' DAY(0)",
			"INTERVAL '1.1234567' SECOND(9,6)", "INTERVAL '1' DAY TO SECOND(10)",
			"INTERVAL '123' YEAR(2)", "INTERVAL '1234' DAY(3)", "INTERVAL '1:2:3' HOUR TO MINUTE" })
	void invalidOrLossySqlTextIsNeverParsable(final String text) {
		assertFalse(Interval.isParsable(text));
		assertThrows(RuntimeException.class, () -> Interval.parse(text));
	}

	@Test
	void qualifiedFractionsAndPrecisionRoundTripExactly() {
		assertEquals(14, Interval.parse("interval '1-2' year(2) to month").getMonthsFull());
		assertEquals(123_456_789, Interval.parse("INTERVAL '1.123456789' SECOND(2,9)").getNanos());
		assertEquals(123_456_000, Interval.parse("INTERVAL '1 2:3:4.123456' DAY(3) TO SECOND(6)").getNanos());
		assertEquals(129_600, Interval.parse("INTERVAL '1.5' DAY").getSecondsFull());
		assertEquals(1, Interval.parse("INTERVAL '0.000000001' SECOND").getNanos());
		assertTrue(Interval.isParsable("INTERVAL '5.' SECOND"));
		assertNull(Interval.parse(null));
		assertNull(Interval.parse(" "));
	}

	@Test
	void baseTextRoundTripsWithoutGuessingTheUnitOfAScalar() {
		for (Interval value : new Interval[] { new Interval(), new Interval(1, 2, 3, 4, 5, 6, 123_456_789),
				new Interval(-1, -2, -3, -4, -5, -6, -1) }) {
			assertTrue(Interval.isParsable(value.toString()));
			assertEquals(value, Interval.parse(value.toString()));
		}
		assertThrows(IllegalArgumentException.class, () -> Interval.parse("1"));
		assertThrows(IllegalArgumentException.class, () -> Interval.parse("'abc'"));
		assertEquals(1, IntervalSecond.parse("1").getSecondsFull());
	}

	private static Calendar calendar(final String zone, final int year, final int month, final int day) {
		final Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone(zone));
		calendar.clear();
		calendar.set(year, month, day);
		return calendar;
	}
}
