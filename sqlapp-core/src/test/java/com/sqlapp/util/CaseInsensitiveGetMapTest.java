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

/**
 * 
 */
package com.sqlapp.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * @author satoh
 *
 */
public class CaseInsensitiveGetMapTest {

	/**
	 * Test method for {@link com.sqlapp.util.CaseInsensitiveMap#put(java.lang.String, java.lang.Object)}.
	 */
	@Test
	public void testPut() {
		CaseInsensitiveGetMap<String> map=new CaseInsensitiveGetMap<String>();
		map.put("aB", "b");
		map.put("Ab", "c");
		assertEquals("b", map.get("aB"));
		assertEquals("c", map.get("Ab"));
		assertEquals("b", map.get("AB"));
	}

	/**
	 * Test method for {@link com.sqlapp.util.CaseInsensitiveMap#clear()}.
	 */
	@Test
	public void testClear() {
		CaseInsensitiveGetMap<String> map=new CaseInsensitiveGetMap<String>();
		map.put("aaaaa", "b");
		map.clear();
		assertTrue(map.size()==0);
	}

	/**
	 * Test method for {@link com.sqlapp.util.CaseInsensitiveMap#entrySet()}.
	 */
	@Test
	public void testEntrySet() {
		CaseInsensitiveGetMap<String> map=new CaseInsensitiveGetMap<String>();
		map.put("aB", "a");
		map.put("Ab", "b");
		map.put("Bc", "c");
		map.put("cD", "d");
		map.put("EF", "e");
		assertEquals("a", map.get("aB"));
		assertEquals("b", map.get("Ab"));
		assertEquals("c", map.get("bC"));
		assertEquals("c", map.get("BC"));
		assertEquals("c", map.get("bc"));
		assertEquals("d", map.get("cD"));
		assertEquals("d", map.get("cd"));
		assertEquals("e", map.get("EF"));
		assertEquals("e", map.get("ef"));
	}
}
