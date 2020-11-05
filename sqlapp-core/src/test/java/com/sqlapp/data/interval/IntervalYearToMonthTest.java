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
package com.sqlapp.data.interval;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class IntervalYearToMonthTest {

	@Test
	public void testIntervalIntIntIntIntIntInt() {
		IntervalYearToMonth interval=new IntervalYearToMonth(5,11);
		assertEquals("5-11", interval.toString());
	}
	
	@Test
	public void testEquals() {
		IntervalYearToMonth interval1=new IntervalYearToMonth(5,12);
		IntervalYearToMonth interval2=new IntervalYearToMonth(6,0);
		assertEquals(interval1, interval2);
	}

}
