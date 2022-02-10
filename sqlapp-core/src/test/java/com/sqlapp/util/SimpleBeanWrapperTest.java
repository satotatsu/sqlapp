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

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.interval.Interval;

public class SimpleBeanWrapperTest {

	@Test
	public void testSimpleBeanWrapper1() {
		SimpleBeanWrapper utils = new SimpleBeanWrapper(
				com.sqlapp.data.interval.Interval.class.getName());
		Interval interval1 = new Interval(2011, 2, 23, 0, 0, 0, 0);
		Interval interval2 = new Interval(0, 0, 0, 0, 0, 0, 0);
		utils.setValue(interval2, "years", 2011);
		utils.setValue(interval2, "months", 2);
		utils.setValue(interval2, "days", 23);
		assertTrue(interval2.equals(interval1));
	}

	@Test
	public void testSimpleBeanWrapper2() {
		SimpleBeanWrapper utils = new SimpleBeanWrapper(
				com.sqlapp.data.interval.Interval.class.getName());
		Interval interval1 = new Interval(2011, 2, 23, 0, 0, 0, 0);
		Interval interval2 = new Interval(0, 0, 0, 0, 0, 0, 0);
		utils.invoke(interval2, "setYears", 2011);
		utils.invoke(interval2, "setMonths", 2);
		utils.invoke(interval2, "setDays", 23);
		assertTrue(interval2.equals(interval1));
	}

	@Test
	public void testSimpleBeanWrapper3() {
		SimpleBeanWrapper utils = new SimpleBeanWrapper(
				com.sqlapp.data.interval.Interval.class.getName());
		Interval interval1 = new Interval(2011, 2, 23, 0, 0, 0, 0);
		Interval interval2 = new Interval(0, 0, 0, 0, 0, 0, 0);
		utils.invoke(interval2, "setYears", "2011");
		utils.invoke(interval2, "setMonths", 2.0d);
		utils.invoke(interval2, "setDays", 23L);
		assertTrue(interval2.equals(interval1));
	}

}
