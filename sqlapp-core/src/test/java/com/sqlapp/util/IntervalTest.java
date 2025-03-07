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

public class IntervalTest {
	@Test
	public void testEquals() {
		Interval interval1=new Interval(1,0,0,0,0,0,0);
		Interval interval2=new Interval(0,12,0,0,0,0,0);
		assertTrue(interval1.equals(interval2));
		assertTrue(interval1.compareTo(interval2)==0);
		//
		interval1=new Interval(0,0,2,0,0,0,0);
		interval2=new Interval(0,0,0,24*2,0,0,0);
		assertTrue(interval1.equals(interval2));
		assertTrue(interval1.compareTo(interval2)==0);
		//
		interval1=new Interval(0,0,0,2,0,0,0);
		interval2=new Interval(0,0,0,0,60*2,0,0);
		assertTrue(interval1.equals(interval2));
		assertTrue(interval1.compareTo(interval2)==0);
		//
		interval1=new Interval(0,0,0,0,3,0,0);
		interval2=new Interval(0,0,0,0,0,60*3,0);
		assertTrue(interval1.equals(interval2));
		assertTrue(interval1.compareTo(interval2)==0);
		//
		interval1=new Interval(0,0,0,0,0,2,0);
		interval2=new Interval(0,0,0,0,0,0,1000000000*2);
		assertTrue(interval1.equals(interval2));
		assertTrue(interval1.compareTo(interval2)==0);
		//
		interval1=new Interval(0,0,0,0,0,2, 1000000000/2);
		interval2=new Interval(0,0,0,0,0,2.5);
		assertTrue(interval1.equals(interval2));
		assertTrue(interval1.compareTo(interval2)==0);
		//
		interval1=new Interval(0,0,2,0,0,0,0);
		interval2=new Interval(0,0,0,0,60*24*2,0,0);
		assertTrue(interval1.equals(interval2));
		assertTrue(interval1.compareTo(interval2)==0);
		//
		interval1=new Interval(0,0,0,0,0,0,0);
		interval1.setYears(3.5);
		interval2=new Interval(3,6,0,0,0,0,0);
		assertTrue(interval1.equals(interval2));
		assertTrue(interval1.compareTo(interval2)==0);
		//
		interval1=new Interval(0,0,3,3,0,0,0);
		interval1.setDays(2.5);
		interval2=new Interval(0,0,2,12,0,0,0);
		assertTrue(interval1.equals(interval2));
		assertTrue(interval1.compareTo(interval2)==0);
		//
		interval1=new Interval(0,0,0,4,4,0,0);
		interval1.setHours(3.5);
		interval2=new Interval(0,0,0,3,30,0,0);
		assertTrue(interval1.equals(interval2));
		assertTrue(interval1.compareTo(interval2)==0);
		//
		interval1=new Interval(0,0,0,0,5,5,0);
		interval1.setMinutes(6.5);
		interval2=new Interval(0,0,0,0,6,30,0);
		assertTrue(interval1.equals(interval2));
		assertTrue(interval1.compareTo(interval2)==0);
		//
		interval1=new Interval(0,0,0,0,0,5,1);
		interval1.setSeconds(6.5);
		interval2=new Interval(0,0,0,0,0,6.5);
		assertTrue(interval1.equals(interval2));
		assertTrue(interval1.compareTo(interval2)==0);
		//
		interval1=new Interval(0,0,0,6,4,5,1);
		interval1.setSeconds((double)24*3600*2 + 3600*12+ 5*60+ 32+0.5);
		interval2=new Interval(0,0,2,12,5,32.5);
		assertTrue(interval1.equals(interval2));
		assertTrue(interval1.compareTo(interval2)==0);
	}

	@Test
	public void testCompareTo() {
		Interval interval1=new Interval(1,0,0,0,0,0,0);
		Interval interval2=new Interval(0,13,0,0,0,0,0);
		assertTrue(interval1.compareTo(interval2)<0);
		assertTrue(interval2.compareTo(interval1)>0);
		//
		interval1=new Interval(0,0,2,0,0,0,0);
		interval2=new Interval(0,0,0,24*3,0,0,0);
		assertTrue(interval1.compareTo(interval2)<0);
		assertTrue(interval2.compareTo(interval1)>0);
		//
		interval1=new Interval(0,0,0,2,0,0,0);
		interval2=new Interval(0,0,0,0,60*3,0,0);
		assertTrue(interval1.compareTo(interval2)<0);
		assertTrue(interval2.compareTo(interval1)>0);
		//
		interval1=new Interval(0,0,0,0,3,0,0);
		interval2=new Interval(0,0,0,0,0,60*4,0);
		assertTrue(interval1.compareTo(interval2)<0);
		assertTrue(interval2.compareTo(interval1)>0);
		//
		interval1=new Interval(0,0,0,0,0,1,0);
		interval2=new Interval(0,0,0,0,0,0,1000000000*2);
		assertTrue(interval1.compareTo(interval2)<0);
		assertTrue(interval2.compareTo(interval1)>0);
		//
		interval1=new Interval(0,0,0,0,0,2, 1000000000/2);
		interval2=new Interval(0,0,0,0,0,2.6);
		assertTrue(interval1.compareTo(interval2)<0);
		assertTrue(interval2.compareTo(interval1)>0);
		//
		interval1=new Interval(0,0,2,0,0,0,0);
		interval2=new Interval(0,0,0,0,60*24*3,0,0);
		assertTrue(interval1.compareTo(interval2)<0);
		assertTrue(interval2.compareTo(interval1)>0);
	}
	
	@Test
	public void testParse() {
		Interval interval1=Interval.parse("Interval '10' year");
		Interval interval2=new Interval(10, 0, 0, 0, 0, 0);
		assertTrue(interval1.equals(interval2));
		//
		interval1=Interval.parse("Interval '10 2' year to month");
		interval2=new Interval(10, 2, 0, 0, 0, 0);
		assertTrue(interval1.equals(interval2));
		//
		interval1=Interval.parse("Interval '-10 2' year to month");
		interval2=new Interval(10, 2, 0, 0, 0, 0).scale(-1);
		assertTrue(interval1.equals(interval2));
		//
		interval1=Interval.parse("Interval '-10-2-29' year to day");
		interval2=new Interval(10, 2, 29, 0, 0, 0).scale(-1);
		assertTrue(interval1.equals(interval2));
		//
		interval1=Interval.parse("Interval '-10-2-29 13' year to hour");
		interval2=new Interval(10, 2, 29, 13, 0, 0).scale(-1);
		assertTrue(interval1.equals(interval2));
		//
		interval1=Interval.parse("Interval '-10-2-29 13:59' year to minute");
		interval2=new Interval(10, 2, 29, 13, 59, 0).scale(-1);
		assertTrue(interval1.equals(interval2));
		//
		interval1=Interval.parse("Interval '-10-2-29 13:59' year to second");
		interval2=new Interval(10, 2, 29, 13, 59, 0).scale(-1);
		assertTrue(interval1.equals(interval2));
		//
		interval1=Interval.parse("Interval -10-2-29 13:59 year to second");
		interval2=new Interval(10, 2, 29, 13, 59, 0).scale(-1);
		assertTrue(interval1.equals(interval2));
		//
		interval1=Interval.parse("Interval '-10-2-29 13:59:23' year to second");
		interval2=new Interval(10, 2, 29, 13, 59, 23).scale(-1);
		assertTrue(interval1.equals(interval2));
		//
		interval1=Interval.parse("Interval '10 2 29 13:59:23' year to second");
		interval2=new Interval(10, 2, 29, 13, 59, 23);
		assertTrue(interval1.equals(interval2));
		//
		interval1=Interval.parse("Interval '29 13:59:23' day to second");
		interval2=new Interval(0, 0, 29, 13, 59, 23);
		assertTrue(interval1.equals(interval2));
		//
		interval1=Interval.parse("Interval '29 13:59:23.585' day to second");
		interval2=new Interval(0, 0, 29, 13, 59, 23.585);
		assertTrue(interval1.equals(interval2));
		//
		interval1=Interval.parse("Interval '13:59:23' hour to second");
		interval2=new Interval(0, 0, 0, 13, 59, 23);
		assertTrue(interval1.equals(interval2));
		//
		interval1=Interval.parse("Interval '59:23' minute to second");
		interval2=new Interval(0, 0, 0, 0, 59, 23);
		assertTrue(interval1.equals(interval2));
		//
		interval1=Interval.parse("Interval '59' minute to second");
		interval2=new Interval(0, 0, 0, 0, 59, 0);
		assertTrue(interval1.equals(interval2));
		//
		interval1=Interval.parse("Interval '59' year");
		interval2=new Interval(0, 59*12, 0, 0, 0, 0);
		assertTrue(interval1.equals(interval2));
		//
		interval1=Interval.parse("Interval '59' day");
		interval2=new Interval(0, 0, 0, 59*24, 0, 0);
		assertTrue(interval1.equals(interval2));
		//
		interval1=Interval.parse("Interval '59' day");
		interval2=new Interval(0, 0, 0, 0, 59*24*60, 0);
		assertTrue(interval1.equals(interval2));
		//
		interval1=Interval.parse("Interval '59' day");
		interval2=new Interval(0, 0, 0, 0, 0, 59*24*60*60);
		assertTrue(interval1.equals(interval2));
	}

	@Test
	public void testSetter() {
		Interval interval1=Interval.parse("Interval '10' year");
		Interval interval2=new Interval(10, 0, 0, 0, 0, 0);
		interval1.setYears(50);
		interval2.setYears(50);
		assertTrue(interval1.equals(interval2));
		//
		interval1.setMonths(11);
		interval2=new Interval(50, 11, 0, 0, 0, 0);
		assertTrue(interval1.equals(interval2));
		//
		interval1.setYears(0);
		interval1.setMonths(26);
		interval2=new Interval(2, 2, 0, 0, 0, 0);
		assertTrue(interval1.equals(interval2));
		//
		interval1.setYears(10);
		interval1.setMonths(26);
		interval2=new Interval(2, 2, 0, 0, 0, 0);
		assertTrue(interval1.equals(interval2));
		//
		interval1.setYears(10);
		interval1.setMonths(0);
		interval1.setSeconds(59);
		interval2=new Interval(10, 0, 0, 0, 0, 59);
		assertTrue(interval1.equals(interval2));
		//
		interval1.setYears(10);
		interval1.setMonths(0);
		interval1.setSeconds(119);
		interval2=new Interval(10, 0, 0, 0, 1, 59);
		assertTrue(interval1.equals(interval2));
		//
		interval1.setYears(10);
		interval1.setMonths(0);
		interval1.setSeconds(3600*2+19);
		interval2=new Interval(10, 0, 0, 2, 0, 19);
		assertTrue(interval1.equals(interval2));
		//
		interval1.setYears(10);
		interval1.setMonths(0);
		interval1.setSeconds(3600*25+19);
		interval2=new Interval(10, 0, 1, 1, 0, 19);
		assertTrue(interval1.equals(interval2));
	}
	
	@Test
	public void testToString() {
		Interval interval1=Interval.parse("Interval '-10-2-29 13' year to hour");
		assertTrue(interval1.toString().equals("-10-2-29 13:0:0"));
		interval1=Interval.parse("Interval '10-2-29 13:59:32' year to second");
		assertTrue(interval1.toString().equals("10-2-29 13:59:32"));
	}
}
