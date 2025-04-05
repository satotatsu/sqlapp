/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CountIteratorTest {

	@Test
	void test() {
		long[] holder = new long[1];
		CountIterable<Long> itr = new CountIterable<Long>(10, cnt -> holder[0] = cnt);
		long i = 0;
		for (long va : itr) {
			assertEquals(i, va);
			System.out.println(i);
			i++;
		}
		assertEquals(10, i);
		assertEquals(9, holder[0]);
	}

	@Test
	void testRange() {
		long[] holder = new long[1];
		CountIterable<Long> itr = new CountIterable<Long>(5, 25, cnt -> holder[0] = cnt);
		long i = 0;
		for (long va : itr) {
			assertEquals(i + 5, va);
			System.out.println(i);
			i++;
		}
		assertEquals(20, i);
		assertEquals(24, holder[0]);
	}

}
