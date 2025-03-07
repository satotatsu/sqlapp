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

import static com.sqlapp.util.CommonUtils.newTimestamp;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.TestCaseBase;
import com.sqlapp.util.DateUtils;
import com.sqlapp.util.Java8DateUtils;

public class ConvertersTest extends TestCaseBase {
	/**
	 * テスト開始時に状態を初期化します。
	 */
	@BeforeEach
	public void setUpTestCaseBase() {
		setTimeZoneJST();
	}

	/**
	 * 全コンバータ変換テスト
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testAll() {
		Converters converters = new Converters();
		Map<Class<?>, Converter<?>> converterMap = converters.getConverterMap();
		for (Map.Entry<Class<?>, Converter<?>> enrty : converterMap.entrySet()) {
			Converter converter = enrty.getValue();
			if (!enrty.getKey().isPrimitive()) {
				assertNull(converter.convertObject(null));
				assertNull(converter.convertString(null));
			} else {
				assertNotNull(converter.convertObject(null));
			}
			converter.convertObject("");
			converter.setDefaultValue(null);
		}
	}

	/**
	 * ZonedDateTime変換テスト
	 */
	@Test
	public void testZonedDateTime() {
		Converters converters = new Converters();
		String dateText = "2011-01-02T12:30:15";
		ZonedDateTime dateTime = converters.convertObject(dateText, ZonedDateTime.class);
		assertEquals(dateText, Java8DateUtils.format(dateTime, "yyyy-MM-dd'T'HH:mm:ss"));
		assertEquals("2011-01-02T12:30:15+09:00[Asia/Tokyo]", dateTime.toString());
		// RFC_1123_DATE_TIME
		dateText = "Tue, 3 Jun 2016 11:05:30 GMT";
		dateTime = converters.convertObject(dateText, ZonedDateTime.class);
		assertEquals("2016-06-03T11:05:30", Java8DateUtils.format(dateTime, "yyyy-MM-dd'T'HH:mm:ss"));
		assertEquals("2016-06-03T11:05:30Z", dateTime.toString());
		// ISO_INSTANT
		dateText = "2011-12-03T10:15:30Z";
		dateTime = converters.convertObject(dateText, ZonedDateTime.class);
		assertEquals("2011-12-03T10:15:30", Java8DateUtils.format(dateTime, "yyyy-MM-dd'T'HH:mm:ss"));
		assertEquals("2011-12-03T10:15:30Z", dateTime.toString());
		//
		dateText = "2011-1-2T2:3:1";
		dateTime = converters.convertObject(dateText, ZonedDateTime.class);
		assertEquals("2011-01-02T02:03:01", Java8DateUtils.format(dateTime, "yyyy-MM-dd'T'HH:mm:ss"));
		assertEquals("2011-01-02T02:03:01+09:00[Asia/Tokyo]", dateTime.toString());
		//
		dateText = "2011-01-02T12:30:15+0000";
		dateTime = converters.convertObject(dateText, ZonedDateTime.class);
		assertEquals("2011-01-02 12:30:15 +00:00[Z]", converters.convertString(dateTime));
		//
		dateText = "2011-01-02T12:30:15+10:00";
		dateTime = converters.convertObject(dateText, ZonedDateTime.class);
		assertEquals("2011-01-02 12:30:15 +10:00[+10:00]", converters.convertString(dateTime));
		//
		dateText = "2011-01-02T12:30:15+09:00[Asia/Tokyo]";
		dateTime = converters.convertObject(dateText, ZonedDateTime.class);
		assertEquals("2011-01-02 12:30:15 +09:00[JST]", converters.convertString(dateTime));
		assertEquals("2011-01-02T12:30:15+09:00[Asia/Tokyo]", dateTime.toString());
		//
		dateText = "2011-01-02T12:30:15+01:00 GMT";
		dateTime = converters.convertObject(dateText, ZonedDateTime.class);
		assertEquals("2011-01-02 12:30:15 +01:00[+01:00]", converters.convertString(dateTime));
		assertEquals("2011-01-02T12:30:15+01:00", dateTime.toString());
		//
		dateText = "2011-01-02T12:30:15.123+0100";
		dateTime = converters.convertObject(dateText, ZonedDateTime.class);
		assertEquals("2011-01-02 12:30:15 +01:00[+01:00]", converters.convertString(dateTime));
		assertEquals("2011-01-02T12:30:15.123+01:00", dateTime.toString());
		assertEquals(123000000, dateTime.getNano());
		//
		dateText = "2011-01-02T12:30:15.000+0100";
		dateTime = converters.convertObject(dateText, ZonedDateTime.class);
		assertEquals("2011-01-02T12:30:15+01:00", dateTime.toString());
		assertEquals(0, dateTime.getNano());
		//
		dateText = "2011-02-02T13:30:16";
		dateTime = converters.convertObject(dateText, ZonedDateTime.class);
		assertEquals("2011-02-02 13:30:16 +09:00[JST]", converters.convertString(dateTime));
		//
		dateText = "2011-02-02 13:30:16 Z";
		dateTime = converters.convertObject(dateText, ZonedDateTime.class);
		assertEquals("2011-02-02 13:30:16 +00:00[Z]", converters.convertString(dateTime));
		//
		dateText = "2011-02-02T13:30";
		dateTime = converters.convertObject(dateText, ZonedDateTime.class);
		assertEquals("2011-02-02 13:30:00 +09:00[JST]", converters.convertString(dateTime));
		//
		dateText = "2011-02-02 13:30";
		dateTime = converters.convertObject(dateText, ZonedDateTime.class);
		assertEquals("2011-02-02 13:30:00 +09:00[JST]", converters.convertString(dateTime));
	}

	/**
	 * OffsetDateTime変換テスト
	 */
	@Test
	public void testOffsetDateTime() {
		Converters converters = new Converters();
		String dateText = "2011-01-02T12:30:15";
		OffsetDateTime dateTime = converters.convertObject(dateText, OffsetDateTime.class);
		assertEquals(dateText, Java8DateUtils.format(dateTime, "yyyy-MM-dd'T'HH:mm:ss"));
		assertEquals("2011-01-02 12:30:15 +09:00", converters.convertString(dateTime));
		assertEquals("2011-01-02T12:30:15+09:00", dateTime.toString());
		// RFC_1123_DATE_TIME
		dateText = "Tue, 3 Jun 2016 11:05:30 GMT";
		dateTime = converters.convertObject(dateText, OffsetDateTime.class);
		assertEquals("2016-06-03T11:05:30", Java8DateUtils.format(dateTime, "yyyy-MM-dd'T'HH:mm:ss"));
		assertEquals("2016-06-03T11:05:30Z", dateTime.toString());
		// ISO_INSTANT
		dateText = "2011-12-03T10:15:30Z";
		dateTime = converters.convertObject(dateText, OffsetDateTime.class);
		assertEquals("2011-12-03T10:15:30", Java8DateUtils.format(dateTime, "yyyy-MM-dd'T'HH:mm:ss"));
		assertEquals("2011-12-03T10:15:30Z", dateTime.toString());
		//
		dateText = "2011-1-2T2:3:1";
		dateTime = converters.convertObject(dateText, OffsetDateTime.class);
		assertEquals("2011-01-02T02:03:01", Java8DateUtils.format(dateTime, "yyyy-MM-dd'T'HH:mm:ss"));
		assertEquals("2011-01-02 02:03:01 +09:00", converters.convertString(dateTime));
		assertEquals("2011-01-02T02:03:01+09:00", dateTime.toString());
		//
		dateText = "2011-01-02T12:30:15+0000";
		dateTime = converters.convertObject(dateText, OffsetDateTime.class);
		assertEquals("2011-01-02 12:30:15 Z", converters.convertString(dateTime));
		//
		dateText = "2011-01-02T12:30:15+10:00";
		dateTime = converters.convertObject(dateText, OffsetDateTime.class);
		assertEquals("2011-01-02 12:30:15 +10:00", converters.convertString(dateTime));
		//
		dateText = "2011-01-02T12:30:15+09:00[Asia/Tokyo]";
		dateTime = converters.convertObject(dateText, OffsetDateTime.class);
		assertEquals("2011-01-02 12:30:15 +09:00", converters.convertString(dateTime));
		assertEquals("2011-01-02T12:30:15+09:00", dateTime.toString());
		//
		dateText = "2011-01-02T12:30:15+01:00 GMT";
		dateTime = converters.convertObject(dateText, OffsetDateTime.class);
		assertEquals("2011-01-02 12:30:15 +01:00", converters.convertString(dateTime));
		assertEquals("2011-01-02T12:30:15+01:00", dateTime.toString());
		//
		dateText = "2011-01-02T12:30:15.123+0100";
		dateTime = converters.convertObject(dateText, OffsetDateTime.class);
		assertEquals("2011-01-02 12:30:15 +01:00", converters.convertString(dateTime));
		assertEquals("2011-01-02T12:30:15.123+01:00", dateTime.toString());
		assertEquals(123000000, dateTime.getNano());
		//
		dateText = "2011-01-02T12:30:15.000+0100";
		dateTime = converters.convertObject(dateText, OffsetDateTime.class);
		assertEquals("2011-01-02T12:30:15+01:00", dateTime.toString());
		assertEquals(0, dateTime.getNano());
		//
		dateText = "2011-02-02T13:30:16";
		dateTime = converters.convertObject(dateText, OffsetDateTime.class);
		assertEquals("2011-02-02 13:30:16 +09:00", converters.convertString(dateTime));
		//
		dateText = "2011-02-02 13:30:16 Z";
		dateTime = converters.convertObject(dateText, OffsetDateTime.class);
		assertEquals("2011-02-02 13:30:16 Z", converters.convertString(dateTime));
		//
		dateText = "2011-02-02T13:30";
		dateTime = converters.convertObject(dateText, OffsetDateTime.class);
		assertEquals("2011-02-02 13:30:00 +09:00", converters.convertString(dateTime));
		//
		dateText = "2011-02-02 13:30";
		dateTime = converters.convertObject(dateText, OffsetDateTime.class);
		assertEquals("2011-02-02 13:30:00 +09:00", converters.convertString(dateTime));
	}

	/**
	 * LocalDateTime変換テスト
	 */
	@Test
	public void testLocalDateTime() {
		Converters converters = new Converters();
		String dateText = "2011-01-02T12:30:15";
		LocalDateTime dateTime = converters.convertObject(dateText, LocalDateTime.class);
		assertEquals(dateText, Java8DateUtils.format(dateTime, "yyyy-MM-dd'T'HH:mm:ss"));
		assertEquals("2011-01-02 12:30:15", converters.convertString(dateTime));
		assertEquals("2011-01-02T12:30:15", dateTime.toString());
		// RFC_1123_DATE_TIME
		dateText = "Tue, 3 Jun 2016 11:05:30 GMT";
		dateTime = converters.convertObject(dateText, LocalDateTime.class);
		assertEquals("2016-06-03T11:05:30", Java8DateUtils.format(dateTime, "yyyy-MM-dd'T'HH:mm:ss"));
		assertEquals("2016-06-03T11:05:30", dateTime.toString());
		// ISO_INSTANT
		dateText = "2011-12-03T10:15:30Z";
		dateTime = converters.convertObject(dateText, LocalDateTime.class);
		assertEquals("2011-12-03T10:15:30", Java8DateUtils.format(dateTime, "yyyy-MM-dd'T'HH:mm:ss"));
		assertEquals("2011-12-03T10:15:30", dateTime.toString());
		//
		dateText = "2011-1-2T2:3:1";
		dateTime = converters.convertObject(dateText, LocalDateTime.class);
		assertEquals("2011-01-02T02:03:01", Java8DateUtils.format(dateTime, "yyyy-MM-dd'T'HH:mm:ss"));
		assertEquals("2011-01-02 02:03:01", converters.convertString(dateTime));
		assertEquals("2011-01-02T02:03:01", dateTime.toString());
		//
		dateText = "2011-01-02T12:30:15+0000";
		dateTime = converters.convertObject(dateText, LocalDateTime.class);
		assertEquals("2011-01-02 12:30:15", converters.convertString(dateTime));
		//
		dateText = "2011-01-02T12:30:15+10:00";
		dateTime = converters.convertObject(dateText, LocalDateTime.class);
		assertEquals("2011-01-02 12:30:15", converters.convertString(dateTime));
		//
		dateText = "2011-01-02T12:30:15+09:00[Asia/Tokyo]";
		dateTime = converters.convertObject(dateText, LocalDateTime.class);
		assertEquals("2011-01-02 12:30:15", converters.convertString(dateTime));
		assertEquals("2011-01-02T12:30:15", dateTime.toString());
		//
		dateText = "2011-01-02T12:30:15+01:00 GMT";
		dateTime = converters.convertObject(dateText, LocalDateTime.class);
		assertEquals("2011-01-02 12:30:15", converters.convertString(dateTime));
		assertEquals("2011-01-02T12:30:15", dateTime.toString());
		//
		dateText = "2011-01-02T12:30:15.123+0100";
		dateTime = converters.convertObject(dateText, LocalDateTime.class);
		assertEquals("2011-01-02 12:30:15", converters.convertString(dateTime));
		assertEquals("2011-01-02T12:30:15.123", dateTime.toString());
		assertEquals(123000000, dateTime.getNano());
		//
		dateText = "2011-01-02T12:30:15.000+0100";
		dateTime = converters.convertObject(dateText, LocalDateTime.class);
		assertEquals("2011-01-02T12:30:15", dateTime.toString());
		assertEquals(0, dateTime.getNano());
		//
		dateText = "2011-02-02T13:30:16";
		dateTime = converters.convertObject(dateText, LocalDateTime.class);
		assertEquals("2011-02-02 13:30:16", converters.convertString(dateTime));
		//
		dateText = "2011-02-02 13:30:16 Z";
		dateTime = converters.convertObject(dateText, LocalDateTime.class);
		assertEquals("2011-02-02 13:30:16", converters.convertString(dateTime));
		//
		dateText = "2011-02-02T13:30";
		dateTime = converters.convertObject(dateText, LocalDateTime.class);
		assertEquals("2011-02-02 13:30:00", converters.convertString(dateTime));
		//
		dateText = "2011-02-02 13:30";
		dateTime = converters.convertObject(dateText, LocalDateTime.class);
		assertEquals("2011-02-02 13:30:00", converters.convertString(dateTime));
	}

	/**
	 * Date変換テスト
	 */
	@Test
	public void testDate() {
		Converters converters = new Converters();
		String dateText = "2011-01-02T12:30:15";
		Date dateTime = converters.convertObject(dateText, Date.class);
		assertEquals(dateText, DateUtils.format(dateTime, "yyyy-MM-dd'T'HH:mm:ss"));
		// RFC_1123_DATE_TIME
		dateText = "Tue, 3 Jun 2016 11:05:30 GMT";
		dateTime = converters.convertObject(dateText, Date.class);
		assertEquals("2016-06-03T20:05:30", DateUtils.format(dateTime, "yyyy-MM-dd'T'HH:mm:ss"));
		assertEquals("2016-06-03 20:05:30", converters.convertString(dateTime));
		// ISO_INSTANT
		dateText = "2011-12-03T10:15:30Z";
		dateTime = converters.convertObject(dateText, Date.class);
		assertEquals("2011-12-03T19:15:30", DateUtils.format(dateTime, "yyyy-MM-dd'T'HH:mm:ss"));
		assertEquals("2011-12-03 19:15:30", converters.convertString(dateTime));
		//
		dateText = "2011-1-2T2:3:1";
		dateTime = converters.convertObject(dateText, Date.class);
		assertEquals("2011-01-02T02:03:01", DateUtils.format(dateTime, "yyyy-MM-dd'T'HH:mm:ss"));
		assertEquals("2011-01-02 02:03:01", converters.convertString(dateTime));
		//
		dateText = "2011-01-02T12:30:15+0000";
		dateTime = converters.convertObject(dateText, Date.class);
		assertEquals("2011-01-02 21:30:15", converters.convertString(dateTime));
		//
		dateText = "2011-01-02T12:30:15+10:00";
		dateTime = converters.convertObject(dateText, Date.class);
		assertEquals("2011-01-02 11:30:15", converters.convertString(dateTime));
		//
		dateText = "2011-01-02T12:30:15+09:00[Asia/Tokyo]";
		dateTime = converters.convertObject(dateText, Date.class);
		assertEquals("2011-01-02 12:30:15", converters.convertString(dateTime));
		assertEquals("Sun Jan 02 12:30:15 JST 2011", dateTime.toString());
		//
		dateText = "2011-01-02T12:30:15+01:00 GMT";
		dateTime = converters.convertObject(dateText, Date.class);
		assertEquals("2011-01-02 20:30:15", converters.convertString(dateTime));
		assertEquals("Sun Jan 02 20:30:15 JST 2011", dateTime.toString());
		//
		dateText = "2011-01-02T12:30:15.123+0100";
		dateTime = converters.convertObject(dateText, Date.class);
		assertEquals("2011-01-02 20:30:15", converters.convertString(dateTime));
		assertEquals("Sun Jan 02 20:30:15 JST 2011", dateTime.toString());
		Calendar cal = Calendar.getInstance();
		cal.setTime(dateTime);
		assertEquals(123, cal.get(Calendar.MILLISECOND));
		//
		dateText = "2011-01-02T12:30:15.000+0100";
		dateTime = converters.convertObject(dateText, Date.class);
		assertEquals("2011-01-02 20:30:15", converters.convertString(dateTime));
		//
		dateText = "2011-02-02T13:30:16";
		dateTime = converters.convertObject(dateText, Date.class);
		assertEquals("2011-02-02 13:30:16", converters.convertString(dateTime));
		//
		dateText = "2011-02-02 13:30:16";
		dateTime = converters.convertObject(dateText, Date.class);
		assertEquals("2011-02-02 13:30:16", converters.convertString(dateTime));
		//
		dateText = "2011-02-02T13:30";
		dateTime = converters.convertObject(dateText, Date.class);
		assertEquals("2011-02-02 13:30:00", converters.convertString(dateTime));
		//
		dateText = "2011-02-02 13:30";
		dateTime = converters.convertObject(dateText, Date.class);
		assertEquals("2011-02-02 13:30:00", converters.convertString(dateTime));
	}

	/**
	 * Timestamp変換テスト
	 */
	@Test
	public void testTimestamp() {
		Converters converters = new Converters();
		String dateText = "2011-01-02 12:30:15.123456789";
		Timestamp timestamp = converters.convertObject(dateText, Timestamp.class);
		assertEquals(dateText, timestamp.toString());
		dateText = "2012-08-04 10:09:37.726";
		timestamp = converters.convertObject(dateText, Timestamp.class);
		assertEquals(dateText, timestamp.toString());
		//
		timestamp = newTimestamp();
		dateText = converters.convertString(timestamp);
		assertEquals(timestamp, converters.convertObject(dateText, Timestamp.class));
	}

	/**
	 * ZonedDateTime変換テスト
	 */
	@Test
	public void testTimestamp2() {
		Converters converters = new Converters();
		String dateText = "2011-01-02T12:30:15";
		Timestamp dateTime = converters.convertObject(dateText, Timestamp.class);
		assertEquals(dateText, DateUtils.format(dateTime, "yyyy-MM-dd'T'HH:mm:ss"));
		// RFC_1123_DATE_TIME
		dateText = "Tue, 3 Jun 2016 11:05:30 GMT";
		dateTime = converters.convertObject(dateText, Timestamp.class);
		assertEquals("2016-06-03T20:05:30", DateUtils.format(dateTime, "yyyy-MM-dd'T'HH:mm:ss"));
		assertEquals("2016-06-03 20:05:30", converters.convertString(dateTime));
		// ISO_INSTANT
		dateText = "2011-12-03T10:15:30Z";
		dateTime = converters.convertObject(dateText, Timestamp.class);
		assertEquals("2011-12-03T19:15:30", DateUtils.format(dateTime, "yyyy-MM-dd'T'HH:mm:ss"));
		assertEquals("2011-12-03 19:15:30", converters.convertString(dateTime));
		//
		dateText = "2011-1-2T2:3:1";
		dateTime = converters.convertObject(dateText, Timestamp.class);
		assertEquals("2011-01-02T02:03:01", DateUtils.format(dateTime, "yyyy-MM-dd'T'HH:mm:ss"));
		assertEquals("2011-01-02 02:03:01", converters.convertString(dateTime));
		//
		dateText = "2011-01-02T12:30:15+0000";
		dateTime = converters.convertObject(dateText, Timestamp.class);
		assertEquals("2011-01-02 21:30:15", converters.convertString(dateTime));
		//
		dateText = "2011-01-02T12:30:15+10:00";
		dateTime = converters.convertObject(dateText, Timestamp.class);
		assertEquals("2011-01-02 11:30:15", converters.convertString(dateTime));
		//
		dateText = "2011-01-02T12:30:15+09:00[Asia/Tokyo]";
		dateTime = converters.convertObject(dateText, Timestamp.class);
		assertEquals("2011-01-02 12:30:15", converters.convertString(dateTime));
		//
		dateText = "2011-01-02T12:30:15+01:00 GMT";
		dateTime = converters.convertObject(dateText, Timestamp.class);
		assertEquals("2011-01-02 20:30:15", converters.convertString(dateTime));
		//
		dateText = "2011-01-02T12:30:15.123+0100";
		dateTime = converters.convertObject(dateText, Timestamp.class);
		assertEquals("2011-01-02 20:30:15.123000000", converters.convertString(dateTime));
		assertEquals(123000000, dateTime.getNanos());
		//
		dateText = "2011-01-02 12:30:15.123456789";
		dateTime = converters.convertObject(dateText, Timestamp.class);
		assertEquals("2011-01-02 12:30:15.123456789", converters.convertString(dateTime));
		assertEquals(123456789, dateTime.getNanos());
		//
		dateText = "2011-01-02T12:30:15.000+0100";
		dateTime = converters.convertObject(dateText, Timestamp.class);
		assertEquals("2011-01-02 20:30:15", converters.convertString(dateTime));
		//
		dateText = "2011-02-02T13:30:16";
		dateTime = converters.convertObject(dateText, Timestamp.class);
		assertEquals("2011-02-02 13:30:16", converters.convertString(dateTime));
		//
		dateText = "2011-02-02 13:30:16.000000000";
		dateTime = converters.convertObject(dateText, Timestamp.class);
		assertEquals("2011-02-02 13:30:16", converters.convertString(dateTime));
		//
		dateText = "2011-02-02T13:30";
		dateTime = converters.convertObject(dateText, Timestamp.class);
		assertEquals("2011-02-02 13:30:00", converters.convertString(dateTime));
		//
		dateText = "2011-02-02 13:30";
		dateTime = converters.convertObject(dateText, Timestamp.class);
		assertEquals("2011-02-02 13:30:00", converters.convertString(dateTime));
	}

	/**
	 * ZonedDateTime変換テスト
	 */
	@Test
	public void testCalendar() {
		Converters converters = new Converters();
		String dateText = "2011-01-02T12:30:15";
		Calendar dateTime = converters.convertObject(dateText, Calendar.class);
		assertEquals("2011-01-02 12:30:15 +09:00[JST]", converters.convertString(dateTime));
		// RFC_1123_DATE_TIME
		dateText = "Tue, 3 Jun 2016 11:05:30 GMT";
		dateTime = converters.convertObject(dateText, Calendar.class);
		assertEquals("2016-06-03 11:05:30 +00:00[UTC]", converters.convertString(dateTime));
		// ISO_INSTANT
		dateText = "2011-12-03T10:15:30Z";
		dateTime = converters.convertObject(dateText, Calendar.class);
		assertEquals("2011-12-03 10:15:30 +00:00[UTC]", converters.convertString(dateTime));
		//
		dateText = "2011-1-2T2:3:1";
		dateTime = converters.convertObject(dateText, Calendar.class);
		assertEquals("2011-01-02 02:03:01 +09:00[JST]", converters.convertString(dateTime));
		//
		dateText = "2011-01-02T12:30:15+0000";
		dateTime = converters.convertObject(dateText, Calendar.class);
		assertEquals("2011-01-02 12:30:15 +00:00[UTC]", converters.convertString(dateTime));
		//
		dateText = "2011-01-02T12:30:15+10:00";
		dateTime = converters.convertObject(dateText, Calendar.class);
		assertEquals("2011-01-02 12:30:15 +10:00[GMT+10:00]", converters.convertString(dateTime));
		//
		dateText = "2011-01-02T12:30:15+09:00[Asia/Tokyo]";
		dateTime = converters.convertObject(dateText, Calendar.class);
		assertEquals("2011-01-02 12:30:15 +09:00[JST]", converters.convertString(dateTime));
		//
		dateText = "2011-01-02T12:30:15+01:00 GMT";
		dateTime = converters.convertObject(dateText, Calendar.class);
		assertEquals("2011-01-02 12:30:15 +01:00[GMT+01:00]", converters.convertString(dateTime));
		//
		dateText = "2011-01-02T12:30:15.123+0100";
		dateTime = converters.convertObject(dateText, Calendar.class);
		assertEquals("2011-01-02 12:30:15 +01:00[GMT+01:00]", converters.convertString(dateTime));
		assertEquals(123, dateTime.get(Calendar.MILLISECOND));
		//
		dateText = "2011-01-02T12:30:15.000+0100";
		dateTime = converters.convertObject(dateText, Calendar.class);
		assertEquals("2011-01-02 12:30:15 +01:00[GMT+01:00]", converters.convertString(dateTime));
		assertEquals(0, dateTime.get(Calendar.MILLISECOND));
		//
		dateText = "2011-02-02T13:30:16";
		dateTime = converters.convertObject(dateText, Calendar.class);
		assertEquals("2011-02-02 13:30:16 +09:00[JST]", converters.convertString(dateTime));
		//
		dateText = "2011-02-02 13:30:16";
		dateTime = converters.convertObject(dateText, Calendar.class);
		assertEquals("2011-02-02 13:30:16 +09:00[JST]", converters.convertString(dateTime));
		//
		dateText = "2011-02-02T13:30";
		dateTime = converters.convertObject(dateText, Calendar.class);
		assertEquals("2011-02-02 13:30:00 +09:00[JST]", converters.convertString(dateTime));
		//
		dateText = "2011-02-02 13:30";
		dateTime = converters.convertObject(dateText, Calendar.class);
		assertEquals("2011-02-02 13:30:00 +09:00[JST]", converters.convertString(dateTime));
	}
}
