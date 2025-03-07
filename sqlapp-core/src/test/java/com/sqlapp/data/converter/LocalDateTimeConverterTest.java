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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.TestCaseBase;

public class LocalDateTimeConverterTest extends TestCaseBase {

	/**
	 * テスト開始時に状態を初期化します。
	 */
	@BeforeEach
	public void setUpTestCaseBase() {
		setTimeZoneJST();
	}

	@Test
	public void testDateTime() {
		LocalDateTimeConverter converter = LocalDateTimeConverter.newInstance().setParseFormats("yyyy-MM-dd'T'HH:mm:ss",
				"yyyy/MM/dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ssZZ");
		String dateText = "2011-01-02T12:30:15";
		LocalDateTime dateTime = converter.convertObject(dateText);
		assertEquals(dateText, converter.convertString(dateTime));
		//
		String dateText2 = "2011/01/02 12:30:15";
		dateTime = converter.convertObject(dateText2);
		assertEquals(dateText, converter.convertString(dateTime));
	}

	@Test
	public void testDateTime2() {
		LocalDateTimeConverter converter = LocalDateTimeConverter.newInstance()
				.setParseFormats("yyyy-MM-dd'T'HH:mm:ssxxxxx");
		converter.setFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		String dateText = "2011-01-02T12:30:15+02:00";
		LocalDateTime dateTime = converter.convertObject(dateText);
		assertEquals("2011-01-02T12:30:15Z", converter.convertString(dateTime));
		//
	}

	@Test
	public void testDateTime3() {
		LocalDateTimeConverter converter = LocalDateTimeConverter.newInstance()
				.setParseFormats("yyyy-MM-dd'T'HH:mm:ssxxxxx", "yyyy-MM-dd");
		converter.setFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		LocalDateTime dateTime = converter.convertObject("2011-01-02");
		assertEquals(dateTime.plusDays(1), converter.convertObject("2011-01-03"));
		dateTime = converter.convertObject("2011-01-02T12:30:00+01:00");
		System.out.println(dateTime);
	}

	@Test
	public void testDateTime4() {
		String format = "yyyy-MM-dd'T'HH:mm:ss.SSS";
		LocalDateTimeConverter converter = LocalDateTimeConverter.newInstance()
				.setParseFormats("yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd");
		converter.setFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		LocalDateTime dateTime = converter.convertObject("2011-01-02");
		assertEquals(dateTime.plusDays(1), converter.convertObject("2011-01-03"));
		String dateText = "2011-01-02T12:30:00.000+01:00";
		dateTime = converter.convertObject(dateText);
		assertEquals("2011-01-02T12:30:00.000", dateTime.format(DateTimeFormatter.ofPattern(format)));
		System.out.println(dateTime);
	}

	/**
	 * 数値の変換テスト
	 */
	@Test
	public void testDateTime5() {
		LocalDateTimeConverter converter = LocalDateTimeConverter.newInstance()
				.setParseFormats("yyyy-MM-dd'T'HH:mm:ssxxxxx", "yyyy-MM-dd");
		Date date = new Date();
		assertEquals(date.getTime(),
				Converters.getDefault().convertObject(converter.convertObject(date.getTime()), Date.class).getTime());
		//
		assertEquals(date.getTime(), Converters.getDefault()
				.convertObject(converter.convertObject("" + date.getTime()), Date.class).getTime());
		//
		assertEquals(date.getTime(), Converters.getDefault()
				.convertObject(converter.convertObject("+" + date.getTime()), Date.class).getTime());
	}
}
