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

class CombinedIterableTest {

	@Test
	void test() {
		List<Integer> list1 = List.of(1, 2);
		List<Integer> list2 = List.of(3, 4);
		List<Integer> list3 = List.of();
		List<Integer> list4 = List.of(5);
		int[] cnt = new int[1];
		cnt[0] = 0;
		CombinedIterable<Integer> iterable = new CombinedIterable<Integer>(List.of(list1, list2, list3, list4), itr -> {
			System.out.println("swith cnt=" + cnt[0]++);
		});
		int j = 0;
		for (Integer i : iterable) {
			System.out.println(i);
			assertEquals(j + 1, i);
			j++;
		}
		assertEquals(5, j);
	}

	@Test
	void closesCurrentIteratorWhenIterationFails() {
		final RuntimeException readFailure = new RuntimeException("read");
		final Exception closeFailure = new Exception("close");
		final AtomicInteger closes = new AtomicInteger();
		final Iterable<Integer> source = () -> new TrackingIterator(List.of(), closes, readFailure, closeFailure);
		final Iterator<Integer> iterator = new CombinedIterable<>(List.of(source)).iterator();

		final RuntimeException thrown = assertThrows(RuntimeException.class, iterator::hasNext);

		assertSame(readFailure, thrown);
		assertEquals(1, closes.get());
		assertEquals(1, thrown.getSuppressed().length);
		assertSame(closeFailure, thrown.getSuppressed()[0]);
	}

	@Test
	void closesEachIteratorOnceWhileSwitchingAndAtCompletion() throws Exception {
		final AtomicInteger closes = new AtomicInteger();
		final Iterable<Integer> first = () -> new TrackingIterator(List.of(1), closes, null, null);
		final Iterable<Integer> second = () -> new TrackingIterator(List.of(2), closes, null, null);
		final Iterator<Integer> iterator = new CombinedIterable<>(List.of(first, second)).iterator();

		assertEquals(1, iterator.next());
		assertEquals(2, iterator.next());
		assertEquals(false, iterator.hasNext());
		((AutoCloseable) iterator).close();
		assertEquals(2, closes.get());
	}

	private static final class TrackingIterator implements Iterator<Integer>, AutoCloseable {
		private final Iterator<Integer> delegate;
		private final AtomicInteger closes;
		private final RuntimeException readFailure;
		private final Exception closeFailure;

		private TrackingIterator(final List<Integer> values, final AtomicInteger closes,
				final RuntimeException readFailure, final Exception closeFailure) {
			this.delegate = values.iterator();
			this.closes = closes;
			this.readFailure = readFailure;
			this.closeFailure = closeFailure;
		}

		@Override
		public boolean hasNext() {
			if (readFailure != null) {
				throw readFailure;
			}
			return delegate.hasNext();
		}

		@Override
		public Integer next() {
			return delegate.next();
		}

		@Override
		public void close() throws Exception {
			closes.incrementAndGet();
			if (closeFailure != null) {
				throw closeFailure;
			}
		}
	}

}
