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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Iterator;
import java.util.LinkedHashSet;

import org.junit.jupiter.api.Test;

/**
 * @author satoh
 *
 */
public class CaseInsensitiveSetTest {

	/**
	 * Test method for
	 * {@link com.sqlapp.util.CaseInsensitiveSet#add(java.lang.String)} .
	 */
	@Test
	public void testAdd() {
		CaseInsensitiveSet set = new CaseInsensitiveSet();
		set.add("aB");
		set.add("Ab");
		assertEquals(1, set.size());
		for (String val : set) {
			assertEquals("aB", val);
		}
	}

	/**
	 * Test method for {@link com.sqlapp.util.CaseInsensitiveSet#clear()}.
	 */
	@Test
	public void testClear() {
		CaseInsensitiveSet set = new CaseInsensitiveSet();
		set.add("aaaaa");
		set.clear();
		assertTrue(set.size() == 0);
	}

	/**
	 * Test method for {@link com.sqlapp.util.CaseInsensitiveSet#entrySet()}.
	 */
	@Test
	public void testEntrySet() {
		CaseInsensitiveSet set = new CaseInsensitiveSet(
				new LinkedHashSet<String>());
		set.add("aB");
		set.add("Ab");
		set.add("Bc");
		set.add("bc");
		set.add("cD");
		set.add("EF");
		set.add("ef");
		Iterator<String> itr = set.iterator();
		assertEquals("aB", itr.next());
		assertEquals("Bc", itr.next());
		assertEquals("cD", itr.next());
		assertEquals("EF", itr.next());
		assertEquals(false, itr.hasNext());
		assertEquals(4, set.size());
	}
}
