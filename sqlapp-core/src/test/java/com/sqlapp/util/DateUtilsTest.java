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

import static com.sqlapp.util.DateUtils.format;
import static com.sqlapp.util.DateUtils.parse;
import static com.sqlapp.util.DateUtils.setDate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Time;
import java.text.ParseException;
import java.util.Date;
import java.util.TimeZone;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DateUtilsTest {
	@BeforeEach
	public void before() {
		TimeZone.setDefault(TimeZone.getTimeZone("GMT"));
	}

	@Test
	public void testFormatDate() throws ParseException {
		Date date = parse("2011-05-02", "yyyy-MM-dd");
		assertEquals("2011-05-02", format(date, "yyyy-MM-dd"));
	}

	@Test
	public void testSetDate() throws ParseException {
		Date date = parse("2011-05-02", "yyyy-MM-dd");
		Date ret = setDate(date, 1);
		assertEquals(parse("2011-05-01"), ret);
	}

	@Test
	public void testTimeTest() throws ParseException {
		Time tm = new Time(0);
		assertEquals("00:00:00", format(tm, "HH:mm:ss"));
		Time tmNext = DateUtils.addDays(tm, 1);
		assertEquals("00:00:00.000", format(tmNext, "HH:mm:ss.SSS"));
		assertTrue(tmNext.compareTo(tm) > 0);
		Time tmNext2 = DateUtils.addMilliSeconds(tmNext, -1);
		assertEquals("23:59:59.999", format(tmNext2, "HH:mm:ss.SSS"));
	}

}
