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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.interval.Interval;
import com.sqlapp.data.interval.IntervalDayToHour;

public class IntervalDayToHourTest {



	@Test
	public void testToDayToMinuteType() {
		IntervalDayToHour interval1=IntervalDayToHour.parse("Interval '10' day");
		Interval interval2=interval1.toInterval();
		assertTrue(interval2.equals(interval1));
	}

	@Test
	public void testParse() {
		IntervalDayToHour interval1=IntervalDayToHour.parse("Interval '10' day");
		IntervalDayToHour interval2=new IntervalDayToHour(10, 0);
		assertTrue(interval1.equals(interval2));
		//
		interval1=IntervalDayToHour.parse("'10 11'");
		interval2=new IntervalDayToHour(10, 11);
		assertTrue(interval1.equals(interval2));
	}

}
