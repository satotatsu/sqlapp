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

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.TestCaseBase;

public class ZonedDateTimeConverterTest extends TestCaseBase {
	/**
	 * テスト開始時に状態を初期化します。
	 */
	@BeforeEach
	public void setUpTestCaseBase() {
		setTimeZoneJST();
	}

	@Test
	public void testDateTime() {
		Converter<ZonedDateTime> converter = ZonedDateTimeConverter.newInstance()
				.setParseFormats("yyyy-MM-dd'T'HH:mm:ss", "yyyy/MM/dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ssZZ");
		String dateText = "2011-01-02T12:30:15";
		ZonedDateTime dateTime = converter.convertObject(dateText);
		assertEquals("2011-01-02T12:30:15+09:00[Asia/Tokyo]", converter.convertString(dateTime));
		//
		String dateText2 = "2011/01/02 12:30:15";
		dateTime = converter.convertObject(dateText2);
		assertEquals("2011-01-02T12:30:15+09:00[Asia/Tokyo]", converter.convertString(dateTime));
	}

	@Test
	public void testDateTime2() {
		ZonedDateTimeConverter converter = ZonedDateTimeConverter.newInstance()
				.setParseFormats("yyyy-MM-dd'T'HH:mm:ssxxxxx");
		String dateText = "2011-01-02T12:30:15+02:00";
		ZonedDateTime dateTime = converter.convertObject(dateText);
		assertEquals(dateText, converter.convertString(dateTime));
		//
	}

	@Test
	public void testDateTime3() {
		ZonedDateTimeConverter converter = ZonedDateTimeConverter.newInstance()
				.setParseFormats("yyyy-MM-dd'T'HH:mm:ssxxxxx", "yyyy-MM-dd");
		ZonedDateTime dateTime = converter.convertObject("2011-01-02");
		assertEquals(dateTime.plusDays(1), converter.convertObject("2011-01-03"));
		dateTime = converter.convertObject("2011-01-02T12:30:00+01:00");
		System.out.println(dateTime);
	}

	@Test
	public void testDateTime4() {
		String format = "yyyy-MM-dd'T'HH:mm:ss.SSSxxxxx";
		Converter<ZonedDateTime> converter = ZonedDateTimeConverter.newInstance()
				.setParseFormats("yyyy-MM-dd'T'HH:mm:ss.SSSxxxxx", "yyyy-MM-dd");
		ZonedDateTime dateTime = converter.convertObject("2011-01-02");
		assertEquals(dateTime.plusDays(1), converter.convertObject("2011-01-03"));
		String dateText = "2011-01-02T12:30:00.000+01:00";
		dateTime = converter.convertObject(dateText);
		assertEquals(dateText, dateTime.format(DateTimeFormatter.ofPattern(format)));
		System.out.println(dateTime);
	}

	/**
	 * 数値の変換テスト
	 */
	@Test
	public void testDateTime5() {
		Converter<ZonedDateTime> converter = ZonedDateTimeConverter.newInstance()
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

	@Test
	public void testDateTimeUTC1() {
		ZonedDateTimeConverter converter = ZonedDateTimeConverter.newInstance()
				.setParseFormats("yyyy-MM-dd'T'HH:mm:ssxxxxx").setUtc(true);
		ZonedDateTime dateTime = converter.convertObject("2011-01-02T12:30:00+01:00");
		assertEquals("2011-01-02T11:30Z", converter.convertString(dateTime));
	}

	@Test
	public void testDateTimeUTC2() {
		ZonedDateTimeConverter converter = ZonedDateTimeConverter.newInstance()
				.setParseFormats("yyyy-MM-dd'T'HH:mm:ssxxxxx");
		ZonedDateTime dateTime = converter.convertObject("2011-01-02T12:30:00+01:00");
		assertEquals("2011-01-02T12:30+01:00", converter.convertString(dateTime));
	}

	@Test
	public void testDateTimeUTC3() {
		ZonedDateTimeConverter converter = ZonedDateTimeConverter.newInstance()
				.setParseFormats("yyyy-MM-dd'T'HH:mm:ss VV");
		ZonedDateTime dateTime = converter.convertObject("2011-01-02T12:30:00 Asia/Tokyo");
		assertEquals("2011-01-02T12:30+09:00[Asia/Tokyo]", converter.convertString(dateTime));
	}
}
