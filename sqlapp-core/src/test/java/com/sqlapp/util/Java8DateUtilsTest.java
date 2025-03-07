/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

public class Java8DateUtilsTest {

	@Test
	public void testTruncateMilisecondLocalDateTime() {
		LocalDateTime date = Java8DateUtils.truncateMilisecond(LocalDateTime.now());
		assertEquals(0, date.getNano());
	}

	@Test
	public void testTruncateMilisecondOffsetDateTime() {
		OffsetDateTime date = Java8DateUtils.truncateMilisecond(OffsetDateTime.now());
		assertEquals(0, date.getNano());
	}

	@Test
	public void testTruncateMilisecondZonedDateTime() {
		ZonedDateTime date = Java8DateUtils.truncateMilisecond(ZonedDateTime.now());
		assertEquals(0, date.getNano());
	}

	@Test
	public void testTruncateTimeOffsetDateTime() {
		OffsetDateTime date = Java8DateUtils.truncateTime(OffsetDateTime.now());
		assertEquals(0, date.getHour());
		assertEquals(0, date.getMinute());
		assertEquals(0, date.getSecond());
		assertEquals(0, date.getNano());
	}

	@Test
	public void testTruncateTimeZonedDateTime() {
		ZonedDateTime date = Java8DateUtils.truncateTime(ZonedDateTime.now());
		assertEquals(0, date.getHour());
		assertEquals(0, date.getMinute());
		assertEquals(0, date.getSecond());
		assertEquals(0, date.getNano());
	}

	@Test
	public void testTruncateTimeLocalDateTime() {
		LocalDateTime date = Java8DateUtils.truncateTime(LocalDateTime.now());
		assertEquals(0, date.getHour());
		assertEquals(0, date.getMinute());
		assertEquals(0, date.getSecond());
		assertEquals(0, date.getNano());
	}

	@Test
	public void testAddSeconds() {
		LocalDateTime date = LocalDateTime.of(2016, 06, 01, 12, 14, 15);
		LocalDateTime ret = Java8DateUtils.addSeconds(date, 4);
		assertEquals(19, ret.getSecond());
	}

	@Test
	public void testAddMinutes() {
		LocalDateTime date = LocalDateTime.of(2016, 06, 01, 12, 14);
		LocalDateTime ret = Java8DateUtils.addMinutes(date, 4);
		assertEquals(18, ret.getMinute());
	}

	@Test
	public void testAddHours() {
		LocalDateTime date = LocalDateTime.of(2016, 06, 01, 12, 14, 15);
		LocalDateTime ret = Java8DateUtils.addHours(date, 4);
		assertEquals(16, ret.getHour());
	}

	@Test
	public void testAddDays() {
		LocalDateTime date = LocalDateTime.of(2016, 06, 01, 12, 14);
		LocalDateTime ret = Java8DateUtils.addDays(date, 4);
		assertEquals(5, ret.getDayOfMonth());
	}

	@Test
	public void testAddMonthsTInt() {
		LocalDateTime date = LocalDateTime.of(2016, 06, 01, 12, 14);
		LocalDateTime ret = Java8DateUtils.addMonths(date, 4);
		assertEquals(10, ret.getMonth().getValue());
		//
		LocalDate date2 = LocalDate.of(2016, 06, 01);
		LocalDate ret2 = Java8DateUtils.addMonths(date2, 4);
		assertEquals(10, ret2.getMonth().getValue());
	}

	@Test
	public void testAddMonthsYearMonthInt() {
		YearMonth date = YearMonth.of(2016, 6);
		YearMonth ret = Java8DateUtils.addMonths(date, 4);
		assertEquals(10, ret.getMonth().getValue());
	}

	@Test
	public void testAddMonthsMonthInt() {
		Month date = Month.of(7);
		Month ret = Java8DateUtils.addMonths(date, 4);
		assertEquals(11, ret.getValue());
	}

	@Test
	public void testAddYearsTInt() {
		LocalDateTime date = LocalDateTime.of(2016, 06, 01, 12, 14);
		LocalDateTime ret = Java8DateUtils.addYears(date, 4);
		assertEquals(2020, ret.getYear());
		//
		LocalDate date2 = LocalDate.of(2016, 06, 01);
		LocalDate ret2 = Java8DateUtils.addYears(date2, 4);
		assertEquals(2020, ret2.getYear());
	}

	@Test
	public void testAddYearsYearMonthInt() {
		YearMonth date = YearMonth.of(2016, 6);
		YearMonth ret = Java8DateUtils.addYears(date, 4);
		assertEquals(2020, ret.getYear());
	}

	@Test
	public void testAddYearsYearInt() {
		Year date = Year.of(2016);
		Year ret = Java8DateUtils.addYears(date, 4);
		assertEquals(2020, ret.getValue());
	}

	@Test
	public void testFormat() {
		LocalDateTime date = LocalDateTime.of(2016, 06, 01, 12, 14, 15);
		String value = Java8DateUtils.format(date, "yyyy-MM-dd HH:mm:ss");
		assertEquals("2016-06-01 12:14:15", value);
	}

}
