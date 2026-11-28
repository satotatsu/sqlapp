/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.interval;

import static org.junit.jupiter.api.Assertions.*;

import java.time.DateTimeException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

/** Stage 3 regressions, reproduced before implementation and now always run. */
class IntervalLegacyDelegationTest {
	@Test
	void parsesYearLeadingPrecision() {
		assertEquals(14, Interval.parse("INTERVAL '1-2' YEAR(2) TO MONTH").getMonthsFull());
	}

	@Test
	void parsesDayLeadingPrecision() {
		assertEquals(5, Interval.parse("INTERVAL '5' DAY(3)").getDays());
	}

	@Test
	void parsesSecondsEndingWithDecimalPoint() {
		assertEquals(5, Interval.parse("INTERVAL '5.' SECOND").getSecondsFull());
	}

	@Test
	void nullIsNotParsable() {
		assertFalse(Interval.isParsable(null));
	}

	@Test
	void fractionalDaysAreNotTruncated() {
		assertEquals(129_600, Interval.parse("INTERVAL '1.5' DAY").getSecondsFull());
	}

	@Test
	void trailingGarbageIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> Interval.parse("INTERVAL '1' DAY xyz!!"));
	}

	@Test
	void punctuationIsNotParsable() {
		assertFalse(Interval.isParsable("INTERVAL '1#2' DAY"));
	}

	@Test
	void unknownUnitIsNotParsable() {
		assertFalse(Interval.isParsable("1 foo"));
	}

	@Test
	void secondCalendarAdditionDoesNotCountMinutesTwice() {
		final Calendar start = utcCalendar();
		assertEquals(start.getTimeInMillis() + 61_000, new IntervalSecond(61).add(start).getTimeInMillis());
	}

	@Test
	void minuteCalendarAdditionDoesNotCountHoursTwice() {
		final Calendar start = utcCalendar();
		assertEquals(start.getTimeInMillis() + 61 * 60_000, new IntervalMinute(61).add(start).getTimeInMillis());
	}

	@Test
	void negativeYearMonthCannotSilentlyBecomeAnAbsolutePositiveDate() {
		final IntervalYearToMonth value = new IntervalYearToMonth(5, 11);
		value.setNegative();
		assertThrows(DateTimeException.class, value::toYearMonth);
	}

	@Test
	void monthCarryDoesNotOverwriteExistingYearsDuringAddition() {
		final IntervalYearToMonth target = new IntervalYearToMonth(1, 11);
		new IntervalMonth(1).add(target);
		assertEquals(24, target.getMonthsFull());
	}

	@Test
	void negativeConstructorArgumentsDetermineTheSign() {
		assertFalse(new IntervalSecond(-1).isPositive());
	}

	@Test
	void mixedSignConstructorArgumentsAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> new IntervalDayToSecond(1, -1, 0, 0));
	}

	@Test
	void calendarAddsTotalMonthsBeforeDays() {
		final Calendar start = utcCalendar();
		start.set(2026, Calendar.JANUARY, 30);
		final Calendar result = new Interval(0, 1, 1, 0, 0, 0).add(start);
		assertEquals(Calendar.MARCH, result.get(Calendar.MONTH));
		assertEquals(1, result.get(Calendar.DAY_OF_MONTH));
		assertEquals(Calendar.JANUARY, start.get(Calendar.MONTH));
		assertEquals(30, start.get(Calendar.DAY_OF_MONTH));
	}

	private static Calendar utcCalendar() {
		final Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
		calendar.clear();
		calendar.set(2026, Calendar.JANUARY, 1);
		return calendar;
	}
}
