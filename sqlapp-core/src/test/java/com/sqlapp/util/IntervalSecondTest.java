/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.interval.Interval;
import com.sqlapp.data.interval.IntervalSecond;

public class IntervalSecondTest {

	@Test
	public void testSecondType() {
		IntervalSecond interval1=IntervalSecond.parse("Interval '529' second");
		Interval interval2=interval1.toInterval();
		assertTrue(interval2.equals(interval1));
	}

	@Test
	public void testParse() {
		IntervalSecond interval1=IntervalSecond.parse("Interval '529' second");
		IntervalSecond interval2=new IntervalSecond(529);
		assertTrue(interval1.equals(interval2));
		//
		interval1=IntervalSecond.parse("'328.5'");
		interval2=new IntervalSecond(328.5);
		assertTrue(interval1.equals(interval2));
		//
		interval1.setSeconds(59);
		interval2.setSeconds(59);
		assertTrue(interval1.equals(interval2));
		//
		interval1=IntervalSecond.parse("Interval '529.5' second");
		assertTrue(interval1.toString().equals("529.5"));
	}

}
