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
public class IntegerConverterTest extends TestCaseBase {

	@BeforeAll
	public static void beforeClass() {
		setTimeZone();
	}

	@BeforeEach
	public void before() {
		setTimeZone();
	}

	Converter<Integer> converter;

	@BeforeEach
	public void setUp() {
		converter = new IntegerConverter();
	}

	@Test
	public void test() {
		assertEquals(10, (Integer) converter.convertObject(() -> 10));
		assertEquals(12, (Integer) converter.convertObject(() -> 12));
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
	public void testConvertObject06() {
		converter.convertObject(Long.valueOf("121312"));
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
