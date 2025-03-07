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

package com.sqlapp.data.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Calendar;
import java.util.Date;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.TestCaseBase;

/**
 * Date変換テスト
 * 
 * @author tatsuo satoh
 * 
 */
public class DateConverterTest extends TestCaseBase {

	@BeforeAll
	public static void beforeClass() {
		setTimeZone();
	}

	@BeforeEach
	public void before() {
		setTimeZone();
	}

	DateConverter converter;

	@BeforeEach
	public void setUp() {
		converter = DateConverter.newInstance()
				.setZonedDateTimeConverter(ZonedDateTimeConverter.newInstance()
						.setParseFormats("yyyy-MM-dd'T'HH:mm:ssxxxx", "yyyy-MM-dd'T'HH:mm:ss'Z'",
								"yyyy-MM-dd'T'HH:mm:ss'UTC'", "yyyy-MM-dd HH:mm:ss.SSS xxxx", "yyyy-MM-dd HH:mm:ss")
						.setFormat("yyyy-MM-dd HH:mm:ss"));
	}

	@Test
	public void test() {
		String text = "2011-05-14 10:40:30";
		Date date = converter.convertObject(text);
		assertEquals(text, converter.convertString(date));
		//
		date = new Date();
		Date convertDate = converter.convertObject(date.getTime());
		assertEquals(date, convertDate);
		date = converter.convertObject("2011-05-14T10:40:30Z");
		assertEquals("2011-05-14 10:40:30", converter.convertString(date));
		date = converter.convertObject("2011-05-14T10:40:30UTC");
		assertEquals("2011-05-14 10:40:30", converter.convertString(date));
		date = converter.convertObject("2012-08-23 05:04:06 UTC");
		assertEquals("2012-08-23 05:04:06", converter.convertString(date));
	}

	/**
	 * equals()のテスト
	 */
	@Test
	public void testEquals01() {
		converter.equals(converter);
	}

	/**
	 * copy()のテスト
	 */
	@Test
	public void testCopy01() {
		converter.copy("12");
	}

	/**
	 * copy()のテスト
	 */
	@Test
	public void testCopy02() {
		converter.copy(null);
	}

	/**
	 * convertObject()のテスト
	 */
	@Test
	public void testConvertObject01() {
		converter.convertObject("1213");
	}

	/**
	 * convertObject()のテスト
	 */
	@Test
	public void testConvertObject02() {
		converter.convertObject("");
	}

	/**
	 * convertObject()のテスト
	 */
	@Test
	public void testConvertObject03() {
		Calendar rollingCalendar = Calendar.getInstance();
		converter.convertObject(rollingCalendar);
	}

	/**
	 * convertObject()のテスト
	 */
	@Test
	public void testConvertObject04() {
		java.util.Date date = new java.util.Date();
		converter.convertObject(date);
	}

	/**
	 * convertObject()のテスト
	 */
	@Test
	public void testConvertObject06() {
		converter.convertObject(Long.valueOf("121312"));
	}

	/**
	 * convertObject()のテスト
	 */
	@Test
	public void testConvertObject07() {
		converter.convertObject("now");
	}

	/**
	 * convertObject()のテスト
	 */
	@Test
	public void testConvertObject08() {
		converter.convertObject("current1");
	}

	/**
	 * convertObject()のテスト
	 */
	@Test
	public void testConvertObject09() {
		converter.convertObject("sys");
	}

	/**
	 * convertObject()のテスト
	 */
	@Test
	public void testConvertObject10() {
		converter.convertObject("'2016-10-21 23:43:50'");
	}

	/**
	 * convertObject()のテスト
	 */
	@Test
	public void testConvertObject11() {
		converter.convertObject("132123");
	}

	/**
	 * convertObject()のテスト
	 */
	@Test
	public void testConvertObject12() {
		converter.convertObject(132123);
	}

}
