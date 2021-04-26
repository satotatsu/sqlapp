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

import java.time.chrono.JapaneseDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.TestCaseBase;

public class JapaneseDateConverterTest extends TestCaseBase {

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
		final JapaneseDateConverter converter=JapaneseDateConverter.newInstance().setParseFormats("yy年MM月dd日", "yyyy-MM-dd").setFormat("Gy年M月d日");
		String dateText="2011-01-02T12:30:15";
		JapaneseDate dateTime=converter.convertObject(dateText);
		assertEquals("平成23年1月2日", converter.convertString(dateTime));
		dateText="平成1年02月01日";
		dateTime=converter.convertObject(dateText);
		assertEquals("平成1年2月1日", converter.convertString(dateTime));
	}
}
