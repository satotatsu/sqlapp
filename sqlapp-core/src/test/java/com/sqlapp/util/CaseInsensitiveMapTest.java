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

import com.sqlapp.util.CaseInsensitiveMap;

/**
 * @author satoh
 *
 */
public class CaseInsensitiveMapTest {

	/**
	 * Test method for {@link com.sqlapp.util.CaseInsensitiveMap#put(java.lang.String, java.lang.Object)}.
	 */
	@Test
	public void testPut() {
		CaseInsensitiveMap<String> map=new CaseInsensitiveMap<String>();
		map.put("aB", "b");
		map.put("Ab", "c");
		assertEquals("c", map.get("aB"));
		assertEquals("c", map.get("AB"));
	}

	/**
	 * Test method for {@link com.sqlapp.util.CaseInsensitiveMap#clear()}.
	 */
	@Test
	public void testClear() {
		CaseInsensitiveMap<String> map=new CaseInsensitiveMap<String>();
		map.put("aaaaa", "b");
		map.clear();
		assertTrue(map.size()==0);
	}

	/**
	 * Test method for {@link com.sqlapp.util.CaseInsensitiveMap#entrySet()}.
	 */
	@Test
	public void testEntrySet() {
		CaseInsensitiveMap<String> map=new CaseInsensitiveMap<String>();
		map.put("aB", "a");
		map.put("Ab", "c");
		map.put("Bc", "b");
		map.put("cD", "c");
		map.put("EF", "e");
		assertEquals("c", map.get("aB"));
		assertEquals("c", map.get("AB"));
		assertEquals("b", map.get("BC"));
	}
}
