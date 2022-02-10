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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.text.ParseException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.test.AbstractTest;

/**
 * Json用のユーティリティのテストケース
 * 
 * 
 */
public class JsonUtilsTest extends AbstractTest{

	/**
	 * 日付型の変換のテストを行います
	 * 
	 * @throws ParseException
	 */
	@Test
	public void testToJsonStringDate() throws ParseException {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		Dummy dummy1 = new Dummy();
		dummy1.date =  Converters.getDefault().convertObject("2011-01-01 12:30:31", Date.class);
		dummy1.calendar = DateUtils.toCalendar(dummy1.date);
		dummy1.dateTime = Converters.getDefault().convertObject("2011-01-01T12:30:31+0900", OffsetDateTime.class);
		dummy1.timeZone = TimeZone.getDefault();
		dummy1.locale = Locale.JAPAN;
		System.out.println(dummy1.dateTime);
		dummy1.bytes = BinaryUtils.toBinary(10);
		String val = JsonUtils.toJsonString(dummy1);
		System.out.println(val);
		Dummy dummy2 = JsonUtils.fromJsonString(val, Dummy.class);
		assertEquals(dummy1, dummy2);
		String expected = getResource("jsonUtil1.txt");
		assertEquals(expected, val);
	}
	
	/**
	 * 日付型の変換のテストを行います
	 * 
	 * @throws ParseException
	 */
	@Test
	public void testToJsonStringDate2() throws ParseException {
		TimeZone.setDefault(TimeZone.getTimeZone("JST"));
		Dummy dummy1 = new Dummy();
		dummy1.date = Converters.getDefault().convertObject("2011-01-01 12:30:31", Date.class);
		dummy1.calendar = DateUtils.toCalendar(dummy1.date);
		dummy1.dateTime = Converters.getDefault().convertObject("2011-01-01T12:30:31+0900", OffsetDateTime.class);
		dummy1.timeZone = TimeZone.getDefault();
		dummy1.locale = Locale.JAPAN;
		System.out.println(dummy1.dateTime);
		dummy1.bytes = BinaryUtils.toBinary(10);
		String val = JsonUtils.toJsonString(dummy1);
		System.out.println(val);
		Dummy dummy2 = JsonUtils.fromJsonString(val, Dummy.class);
		assertEquals(dummy1, dummy2);
		String expected = getResource("jsonUtil2.txt");
		assertEquals(expected, val);
	}


	/*
	 * @Test public void testDate() { Date date =
	 * JsonUtils.toDate("2012-08-23 05:04:06 UTC");
	 * assertEquals("2012-08-23 05:04:06", Converters.getDefault()
	 * .convertObject(date, String.class)); }
	 */

	public static class Dummy {
		public Date date;
		public Calendar calendar;
		public OffsetDateTime dateTime;
		public TimeZone timeZone;
		public Locale locale;
		public byte[] bytes;

		public boolean equals(Object obj) {
			if (obj == null) {
				return false;
			}
			if (obj == this) {
				return true;
			}
			if (!(obj instanceof Dummy)) {
				return false;
			}
			Dummy cst = (Dummy) obj;
			if (!CommonUtils.eq(this.date, cst.date)) {
				return false;
			}
			if (!CommonUtils
					.eq(this.calendar.getTime(), cst.calendar.getTime())) {
				return false;
			}
			if (!CommonUtils.eq(this.dateTime.toInstant(),
					cst.dateTime.toInstant())) {
				return false;
			}
			if (!Arrays.equals(this.bytes, cst.bytes)) {
				return false;
			}
			return true;
		}
	}

}
