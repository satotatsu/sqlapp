/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.iterable;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.util.iterator.Iterators;

class CountConvertIterableTest {

	@Test
	void testZero() {
		CountConvertIterable<Long, String> iterable = new CountConvertIterable<>(Iterators.range(0L), (index, val) -> {
			return "a" + val;
		});
		long i = 0;
		for (String val : iterable) {
			assertEquals("a" + i, val);
			i++;
		}
		assertEquals(0, i);
	}

	@Test
	void test() {
		CountConvertIterable<Long, String> iterable = new CountConvertIterable<>(Iterators.range(10L), (index, val) -> {
			return "a" + val;
		});
		long i = 0;
		for (String val : iterable) {
			assertEquals("a" + i, val);
			i++;
		}
		assertEquals(10, i);
	}

}
