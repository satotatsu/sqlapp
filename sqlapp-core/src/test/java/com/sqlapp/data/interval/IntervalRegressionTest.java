/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.interval;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/** Repaired regressions; these must run in the ordinary test task. */
class IntervalRegressionTest {
	@Test
	void timestampAdditionReturnsComputedDateWithoutChangingInput() {
		final Timestamp input = Timestamp.valueOf("2026-01-01 00:00:00.123456789");
		assertEquals(Timestamp.valueOf("2026-01-02 00:00:00.123456789"), new IntervalDay(1).add(input));
		assertEquals(Timestamp.valueOf("2026-01-01 00:00:00.123456789"), input);
	}

	@Test
	void timestampAdditionCarriesFractionalSeconds() {
		assertEquals(Timestamp.valueOf("2026-01-01 00:00:01.3"),
				new IntervalSecond(0, 500_000_000).add(Timestamp.valueOf("2026-01-01 00:00:00.8")));
	}

	@Test
	void javaTimeAdditionRespectsNegativeSign() {
		assertEquals(LocalDate.of(2026, 1, 1), IntervalDay.parse("-1").add(LocalDate.of(2026, 1, 2)));
	}

	@Test
	void javaTimeAdditionKeepsNanos() {
		assertEquals(LocalDateTime.of(2026, 1, 1, 0, 0, 0, 123_456_789),
				new IntervalSecond(0, 123_456_789).add(LocalDateTime.of(2026, 1, 1, 0, 0)));
	}

	@Test
	void minuteToSecondConversionRetainsTotalMinutesAndNanos() {
		final Interval source = new Interval(0, 0, 1, 2, 3, 4, 123_456_789);
		assertEquals(source.getSecondsFull(), IntervalMinuteToSecond.toMinuteToSecondType(source).getSecondsFull());
		assertEquals(source.getNanos(), IntervalMinuteToSecond.toMinuteToSecondType(source).getNanos());
	}

	@Test
	void intervalAdditionCarriesWithoutOverwritingExistingMinutes() {
		final Interval target = new Interval(0, 0, 0, 0, 1, 20);
		new Interval(0, 0, 0, 0, 1, 50).add(target);
		assertEquals(190, target.getSecondsFull());
	}

	@Test
	void dayToSecondTextRetainsNineFractionalDigits() {
		final IntervalDayToSecond value = new IntervalDayToSecond(0, 0, 0, 1, 123_456_789);
		assertEquals("0 0:0:1.123456789", value.toString());
		assertEquals(value.getNanos(), IntervalDayToSecond.parse(value.toString()).getNanos());
	}

	@Test
	void timeMultiplicityDoesNotMultiplyOriginalTime() {
		assertEquals(Time.valueOf("03:00:00"), new IntervalHour(1).add(Time.valueOf("01:00:00"), 2));
	}

	@Test
	void hourTextDoesNotCountDaysTwice() {
		assertEquals("24", new IntervalHour(24).toString());
	}

	@Test
	void equalsDistinguishesAdjacentNanoseconds() {
		assertNotEquals(new IntervalSecond(0, 1), new IntervalSecond(0, 2));
	}

	@Test
	void negativeIntervalsSortBySignedValue() {
		assertTrue(IntervalDay.parse("-2").compareTo(IntervalDay.parse("-1")) < 0);
	}

	@Test
	void scalingPreservesOriginalSign() {
		assertFalse(IntervalDay.parse("-1").scale(2).isPositive());
	}

	@Test
	void monthDayAdditionIncludesDayOnlyIntervals() {
		assertEquals(MonthDay.of(1, 2), new IntervalDay(1).add(MonthDay.of(1, 1), 1));
	}

	@Test
	@ResourceLock(Resources.LOCALE)
	void sqlParsingDoesNotDependOnTurkishLocale() {
		final Locale original = Locale.getDefault();
		try {
			Locale.setDefault(Locale.forLanguageTag("tr-TR"));
			assertEquals(60, Interval.parse("interval '1' minute").getSecondsFull());
		} finally {
			Locale.setDefault(original);
		}
	}

	@Test
	void secondTextDoesNotRoundAwayNanoseconds() {
		assertEquals("0.000000001", new IntervalSecond(0, 1).toString());
	}
}
