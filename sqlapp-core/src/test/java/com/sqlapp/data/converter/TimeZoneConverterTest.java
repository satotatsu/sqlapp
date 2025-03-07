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

import java.util.TimeZone;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.TestCaseBase;

/**
 * TimeZone変換テスト
 * 
 * @author tatsuo satoh
 * 
 */
public class TimeZoneConverterTest extends TestCaseBase {
	Converter<TimeZone> converter;

	/**
	 * セットアップ
	 */
	@BeforeEach
	public void setUp() {
		converter = new TimeZoneConverter();
	}

	/**
	 * 変換テスト
	 */
	@Test
	public void test() {
		assertEquals(TimeZone.getTimeZone("Asia/Tokyo"), converter.convertObject("Asia/Tokyo"));
		//
		assertEquals("Asia/Tokyo", converter.convertString(TimeZone.getTimeZone("Asia/Tokyo")));
	}
}
