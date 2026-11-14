/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.interval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.converter.Converters;

/**
 * Intervals whose time part is a whole number of minutes, hours or days were
 * reset to zero when copied, and the component setters dropped exact
 * multiples of 60 / 24 / 12.
 */
class IntervalCopyTest {

	@ParameterizedTest
	@CsvSource({ "0,0,1,0,0,0", "0,0,0,24,0,0", "0,0,0,0,0,60", "0,0,0,0,60,0", "0,0,0,0,0,3600", "0,0,0,1,0,0",
			"0,0,2,3,4,5", "0,12,0,0,0,0", "1,0,0,0,0,0", "0,0,0,0,0,86400", "0,0,30,23,59,59", "0,25,0,0,0,0",
			"0,0,0,0,0,0" })
	void copiesKeepTheValue(final int years, final int months, final int days, final int hours, final int minutes,
			final int seconds) {
		final Interval positive = new Interval(years, months, days, hours, minutes, seconds, 1000);
		assertEquals(positive, positive.toInterval());
		assertEquals(positive, Converters.getDefault().getConverter(Interval.class).convertObject(positive));
		final Interval negative = new Interval(years, months, days, hours, minutes, seconds, 1000).scale(-1);
		final Interval copy = negative.toInterval();
		assertEquals(negative, copy);
		assertFalse(copy.isPositive());
	}

	@Test
	void componentSettersCarryExactMultiples() {
		final Interval interval = new Interval();
		interval.setSeconds(60);
		assertEquals(60, interval.getSecondsFull());
		interval.setMinutes(60);
		assertEquals(1, interval.getHours());
		assertEquals(0, interval.getMinutes());
		interval.setHours(24);
		assertEquals(1, interval.getDays());
		assertEquals(0, interval.getHours());
		final Interval months = new Interval();
		months.setMonths(12);
		assertEquals(1, months.getYears());
		assertEquals(0, months.getMonths());
	}
}
