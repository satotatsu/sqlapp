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
package com.sqlapp.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class UpperStringMapTest {

	@Test
	public void testContainsKeyObject() {
		UpperMap<String> map=new UpperMap<String>();
		map.put("aaaa", "a");
		assertTrue(map.containsKey("aaaa"));
	}

	@Test
	public void testGetObject() {
		UpperMap<String> map=new UpperMap<String>();
		map.put("aaaa", "a");
		assertEquals(map.get("aaaa"), "a");
		assertEquals(map.get("Aaaa"), "a");
		assertEquals(map.get("AAAA"), "a");
		map.put("AAAA", "b");
		assertEquals(map.get("aaaa"), "b");
		assertEquals(map.get("Aaaa"), "b");
		assertEquals(map.get("AAAA"), "b");
	}

	@Test
	public void testRemoveObject() {
		UpperMap<String> map=new UpperMap<String>();
		map.put("aaaa", "a");
		map.put("aaab", "b");
		assertTrue(map.containsKey("AAAA"));
		map.remove("aaaa");
		assertTrue(map.get("aaaa")==null);
	}

}
