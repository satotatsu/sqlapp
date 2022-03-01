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

package com.sqlapp.data.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.chrono.JapaneseEra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.TestCaseBase;

public class JapaneseEraConverterTest extends TestCaseBase {

	/**
	 * テスト開始時に状態を初期化します。
	 */
	@Override
	@BeforeEach
	public void setUpTestCaseBase() {
		setTimeZoneJST();
	}
	
	@Test
	public void testDateTime() {
		final JapaneseEraConverter converter=JapaneseEraConverter.newInstance();
		String dateText="明治";
		JapaneseEra dateTime=converter.convertObject(dateText);
		assertEquals(JapaneseEra.MEIJI, dateTime);
		dateText="大正";
		dateTime=converter.convertObject(dateText);
		assertEquals(JapaneseEra.TAISHO, dateTime);
		dateText="昭和";
		dateTime=converter.convertObject(dateText);
		assertEquals(JapaneseEra.SHOWA, dateTime);
		dateText="平成";
		dateTime=converter.convertObject(dateText);
		assertEquals(JapaneseEra.HEISEI, dateTime);
		dateText="令和";
		dateTime=converter.convertObject(dateText);
		assertEquals(JapaneseEra.values()[4], dateTime);
	}
}
