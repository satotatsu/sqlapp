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

import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.converter.Converter;
import com.sqlapp.data.converter.LocaleConverter;

/**
 * Locale変換テスト
 * 
 * @author tatsuo satoh
 * 
 */
public class LocaleConverterTest {
	Converter<Locale> converter;

	/**
	 * セットアップ
	 */
	@BeforeEach
	public void setUp() {
		converter = new LocaleConverter();
	}

	/**
	 * 変換テスト
	 */
	@Test
	public void test() {
		assertEquals(Locale.JAPAN, converter.convertObject("ja_JP"));
		assertEquals(Locale.JAPAN, converter.convertObject("ja-JP"));
		assertEquals(Locale.JAPANESE, converter.convertObject("ja"));
		//
		assertEquals("ja-JP", converter.convertString(Locale.JAPAN));
		assertEquals("ja", converter.convertString(Locale.JAPANESE));
		assertEquals("nb-NO", converter.convertString(Locale.forLanguageTag("no-NO")));
	}
}
