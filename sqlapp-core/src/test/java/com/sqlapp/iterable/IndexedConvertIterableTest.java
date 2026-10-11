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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.sqlapp.util.iterator.Iterators;

class IndexedConvertIterableTest {

	@Test
	void testZero() {
		IndexedConvertIterable<Long, String> iterable = new IndexedConvertIterable<>(Iterators.range(0L),
				(index, val) -> {
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
		IndexedConvertIterable<Long, String> iterable = new IndexedConvertIterable<>(Iterators.range(10L),
				(index, val) -> {
					return "a" + val;
				});
		long i = 0;
		for (String val : iterable) {
			assertEquals("a" + i, val);
			i++;
		}
		assertEquals(10, i);
	}

	@Test
	void closesSourceAndPreservesConversionFailure() {
		final RuntimeException conversionFailure = new RuntimeException("convert");
		final Exception closeFailure = new Exception("close");
		final AtomicInteger closes = new AtomicInteger();
		final class ClosingIterator implements Iterator<Integer>, AutoCloseable {
			private final Iterator<Integer> delegate = List.of(1).iterator();

			@Override
			public boolean hasNext() {
				return delegate.hasNext();
			}

			@Override
			public Integer next() {
				return delegate.next();
			}

			@Override
			public void close() throws Exception {
				closes.incrementAndGet();
				throw closeFailure;
			}
		}
		final Iterator<String> iterator = new IndexedConvertIterable<Integer, String>(
				() -> new ClosingIterator(), (index, value) -> {
					throw conversionFailure;
				}).iterator();

		final RuntimeException thrown = assertThrows(RuntimeException.class, iterator::next);

		assertSame(conversionFailure, thrown);
		assertEquals(1, closes.get());
		assertEquals(1, thrown.getSuppressed().length);
		assertSame(closeFailure, thrown.getSuppressed()[0]);
	}

}
