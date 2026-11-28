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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

import com.sqlapp.iterable.CountIterable;

class CountIteratorTest {

	@Test
	void repeatedChecksDoNotAdvanceAndNextWorksWithoutChecks() {
		final var iterator = new CountIterable<Long>(5, 7, value -> value).iterator();
		assertTrue(iterator.hasNext());
		assertTrue(iterator.hasNext());
		assertEquals(5L, iterator.next());
		assertEquals(6L, iterator.next());
		assertFalse(iterator.hasNext());
		assertFalse(iterator.hasNext());
		assertThrows(NoSuchElementException.class, iterator::next);
	}

	@Test
	void emptyAndReversedRangesAreExhausted() {
		for (long start : new long[] { 0, 1 }) {
			final var iterator = new CountIterable<Long>(start, 0, value -> value).iterator();
			assertFalse(iterator.hasNext());
			assertThrows(NoSuchElementException.class, iterator::next);
		}
	}

	@Test
	void upperLongBoundaryDoesNotWrapAround() {
		final var iterator = new CountIterable<Long>(Long.MAX_VALUE - 1, Long.MAX_VALUE, value -> value).iterator();
		assertEquals(Long.MAX_VALUE - 1, iterator.next());
		assertFalse(iterator.hasNext());
		assertThrows(NoSuchElementException.class, iterator::next);
	}

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
