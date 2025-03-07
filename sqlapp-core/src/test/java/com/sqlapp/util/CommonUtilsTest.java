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

package com.sqlapp.util;

import static com.sqlapp.util.CommonUtils.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;

public class CommonUtilsTest {

	@Test
	public void testSystemEnv() {
		Map<String, String> map = CommonUtils.getSystemEnv();
		assertTrue(map.size() > 0);
	}

	/**
	 * Test method for
	 * {@link com.sqlapp.util.StringUtils#trim(java.lang.String)}.
	 */
	@Test
	public void testEq() {
		int[] intArr1 = new int[] { 1, 2, 3 };
		int[] intArr2 = new int[] { 1, 2, 3 };
		assertTrue(CommonUtils.eq(intArr1, intArr2));
		byte[] byteArr1 = new byte[] { 1, 2, 3 };
		byte[] byteArr2 = new byte[] { 1, 2, 3 };
		assertTrue(CommonUtils.eq(byteArr1, byteArr2));
		String str1 = "ab";
		String str2 = new StringBuilder().append('a').append('b').toString();
		assertTrue(CommonUtils.eq(str1, str2));
	}

	/**
	 * Test method for
	 * {@link com.sqlapp.util.StringUtils#trim(java.lang.String)}.
	 */
	@Test
	public void testTrim() {
		assertEquals(CommonUtils.trim("    AaaaBbbb    "), "AaaaBbbb");
		assertEquals(CommonUtils.trim("        "), "");
		assertEquals(CommonUtils.trim("  \t    "), "");
		assertEquals(CommonUtils.trim("  \t \n   "), "");
		assertEquals(CommonUtils.trim("  \t \n   ", ' ', '\t', '\n'), "");
		assertEquals(CommonUtils.trim("  \t \n a  ", ' ', '\t', '\n'),
				"a");
		assertEquals(CommonUtils.trim(null), null);
	}

	@Test
	public void testLeftString() {
		assertEquals(CommonUtils.left("abcdef", 3), "abc");
		assertEquals(CommonUtils.left("abcdef", 10), "abcdef");
	}

	@Test
	public void testRightString() {
		assertEquals(CommonUtils.right("abcdef", 3), "def");
		assertEquals(CommonUtils.right("abcdef", 10), "abcdef");
	}

	/**
	 * Test method for
	 * {@link com.sqlapp.util.StringUtils#rtrim(java.lang.String, char)}.
	 */
	@Test
	public void testRtrimStringChar() {
		assertEquals(CommonUtils.rtrim("AaaaBbbb----", '-'), "AaaaBbbb");
		assertEquals(CommonUtils.rtrim(null), null);
	}

	/**
	 * Test method for
	 * {@link com.sqlapp.util.CommonUtils#rtrim(java.lang.String)}.
	 */
	@Test
	public void testRtrimString() {
		assertEquals(CommonUtils.rtrim("AaaaBbbb    "), "AaaaBbbb");
	}

	/**
	 * Test method for
	 * {@link com.sqlapp.util.CommonUtils#ltrim(java.lang.String, char)}.
	 */
	@Test
	public void testLtrimStringChar() {
		assertEquals(CommonUtils.ltrim("----AaaaBbbb", '-'), "AaaaBbbb");
	}

	/**
	 * Test method for
	 * {@link com.sqlapp.util.CommonUtils#ltrim(java.lang.String)}.
	 */
	@Test
	public void testLtrimString() {
		assertEquals(CommonUtils.ltrim("    AaaaBbbb"), "AaaaBbbb");
		assertEquals(CommonUtils.ltrim(null), null);
	}

	@Test
	public void testUnwrap() {
		assertEquals(CommonUtils.unwrap("'aaa'", "'", "'"), "aaa");
		assertEquals(CommonUtils.unwrap("'aaa'", "'"), "aaa");
		assertEquals(CommonUtils.unwrap("'aaa'", '\''), "aaa");
		assertEquals(CommonUtils.unwrap("@@@aaaBB", "@@@", "BB"), "aaa");
	}

	/**
	 * Test method for {@link com.sqlapp.util.CommonUtils#getChars(char, int)}.
	 */
	@Test
	public void testGetChars() {
		assertTrue(eq(CommonUtils.getChars('a', 5), "aaaaa".toCharArray()));
	}

	/**
	 * Test method for {@link com.sqlapp.util.CommonUtils#isBlank(char, int)}.
	 */
	@Test
	public void testIsBlank() {
		assertTrue(CommonUtils.isBlank("  "));
		assertTrue(CommonUtils.isBlank("  \t  "));
		assertTrue(CommonUtils.isBlank("  \t  \n"));
		assertFalse(CommonUtils.isBlank("  \t a \n"));
	}

	@Test
	public void testEnumMap() {
		// Map<Types, String> map=CommonUtils.enumMap(Types.class);
		Map<DataType, String> map = CommonUtils.map();
		for (DataType type : DataType.values()) {
			map.put(type, type.toString());
		}
		int cnt = 200000;
		long start = System.currentTimeMillis();
		for (int i = 0; i < cnt; i++) {
			map.get(DataType.ARRAY);
			map.get(DataType.ROWVERSION);
			map.get(DataType.BIGINT);
			map.get(DataType.VARCHAR_IGNORECASE);
			map.get(DataType.BIGSERIAL);
			map.get(DataType.VARCHAR);
			map.get(DataType.BINARY);
			map.get(DataType.VARBINARY);
			map.get(DataType.BIT);
			map.get(DataType.UUID);
		}
		long end = System.currentTimeMillis();
		System.out.println((end - start));
	}

	@Test
	public void testCoalesce() {
		assertEquals("OK", CommonUtils.coalesce("OK"));
		assertEquals("OK", CommonUtils.coalesce(null, "OK"));
		assertEquals("OK", CommonUtils.coalesce(null, null, "OK"));
		assertEquals("OK", CommonUtils.coalesce(null, null, null, "OK"));
		assertEquals("OK", CommonUtils.coalesce(null, null, null, null, "OK"));
	}

	@Test
	public void testNotEmpty() {
		assertEquals("OK", CommonUtils.notEmpty("OK"));
		assertEquals("OK", CommonUtils.notEmpty(null, "OK"));
		assertEquals("OK", CommonUtils.notEmpty(null, null, "OK"));
		assertEquals("OK", CommonUtils.notEmpty(null, null, null, "OK"));
		assertEquals("OK", CommonUtils.notEmpty(null, null, null, null, "OK"));
		//
		assertEquals("OK", CommonUtils.notEmpty(null, "OK"));
		assertEquals("OK", CommonUtils.notEmpty(null, "", "OK"));
		assertEquals("OK", CommonUtils.notEmpty(null, "", null, "OK"));
		assertEquals("OK", CommonUtils.notEmpty(null, null, "", null, "OK"));
	}

	@Test
	public void testMin() {
		assertEquals(1, CommonUtils.min(2, 1));
		assertEquals(3, CommonUtils.min(3, 4));
		assertEquals(3, CommonUtils.min(5, 4, 3));
		assertEquals(3, CommonUtils.min(4, 5, 3));
		assertEquals(3, CommonUtils.min(3, 5, 4));
		assertEquals(2, CommonUtils.min(5, 4, 3, 2));
		assertEquals(2, CommonUtils.min(2, 4, 3, 5));
		//
		assertEquals(1L, CommonUtils.min(2L, 1L));
		assertEquals(3L, CommonUtils.min(3L, 4L));
		assertEquals(3L, CommonUtils.min(5L, 4L, 3L));
		assertEquals(3L, CommonUtils.min(4L, 5L, 3L));
		assertEquals(3L, CommonUtils.min(3L, 5L, 4L));
		assertEquals(2L, CommonUtils.min(5L, 4L, 3L, 2L));
	}

	@Test
	public void testMax() {
		assertEquals(2, CommonUtils.max(2, 1));
		assertEquals(4, CommonUtils.max(3, 4));
		assertEquals(5, CommonUtils.max(5, 4, 3));
		assertEquals(5, CommonUtils.max(4, 5, 3));
		assertEquals(5, CommonUtils.max(3, 5, 4));
		assertEquals(5, CommonUtils.max(5, 4, 3, 2));
		assertEquals(5, CommonUtils.max(2, 4, 3, 5));
		//
		assertEquals(2L, CommonUtils.max(2L, 1L));
		assertEquals(4L, CommonUtils.max(3L, 4L));
		assertEquals(5L, CommonUtils.max(5L, 4L, 3L));
		assertEquals(5L, CommonUtils.max(4L, 5L, 3L));
		assertEquals(5L, CommonUtils.max(3L, 5L, 4L));
		assertEquals(5L, CommonUtils.max(5L, 4L, 3L, 2L));
	}

	@Test
	public void testParseKeyValue() {
		Map<String, String> map = CommonUtils
				.parseKeyValue("a;key1= val1 ; key2=val2;");
		assertEquals(2, map.size());
		assertEquals("val1", map.get("key1"));
		assertEquals("val2", map.get("key2"));
	}

	@Test
	public void testReverse() {
		assertEquals("cba", CommonUtils.reverse("abc"));
		assertEquals("c", CommonUtils.reverse("c"));
	}

	@Test
	public void testInitCap() {
		assertEquals("Abc", CommonUtils.initCap("abc"));
	}

	@Test
	public void testGetLocale() {
		assertEquals(Locale.JAPAN, CommonUtils.getLocale("ja_jp"));
		assertEquals(Locale.JAPAN, CommonUtils.getLocale("ja-JP"));
		assertEquals(Locale.JAPANESE, CommonUtils.getLocale("ja"));
		assertNotNull(CommonUtils.getLocale("no"));
		assertEquals(CommonUtils.getLocale("nb"), CommonUtils.getLocale("no"));
	}

	@Test
	public void testLEN_16MB() {
		assertEquals(16777215, CommonUtils.LEN_16MB - 1);
	}

	@Test
	public void testSetAnd() {
		Set<String> set1 = CommonUtils.set("a", "b", "c");
		Set<String> set2 = CommonUtils.set("b", "c", "d");
		Set<String> ret = CommonUtils.and(set1, set2);
		assertEquals(2, ret.size());
		assertTrue(ret.contains("b"));
		assertTrue(ret.contains("c"));
	}

	@Test
	public void testScale() {
		assertEquals(0, scale(0));
		assertEquals(1, scale(10));
		assertEquals(1, scale(15));
		assertEquals(2, scale(100));
	}

	private static int scale(int size) {
		size = Math.abs(size);
		if (size == 0) {
			return 0;
		}
		return (int) Math.log10(size);
	}
}
