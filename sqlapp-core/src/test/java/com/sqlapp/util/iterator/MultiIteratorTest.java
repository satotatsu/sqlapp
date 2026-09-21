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

package com.sqlapp.util.iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class MultiIteratorTest {

	@Test
	void test() {
		List<List<Integer>> listList = List.of(List.of(), List.of(1, 2), List.of(3, 4), List.of());
		test(listList, "a1a2a3a4");
		listList = List.of();
		test(listList, "");
		listList = List.of(List.of(1));
		test(listList, "a1");
		listList = List.of(List.of());
		test(listList, "");
		listList = List.of();
		test(listList, "");
	}

	private void test(List<List<Integer>> listList, String result) {
		Function<List<Integer>, Iterator<String>> func = (a -> {
			return a.stream().map(val -> "a" + val).collect(Collectors.toList()).iterator();
		});
		MultiIterable<List<Integer>, String> itr = new MultiIterable<List<Integer>, String>(listList, func);
		StringBuilder builder = new StringBuilder();
		for (String val : itr) {
			builder.append(val);
		}
		assertEquals(result, builder.toString());
	}

	@Test
	void listBackedIterableCanBeTraversedMoreThanOnce() {
		final MultiIterable<List<Integer>, Integer> iterable = new MultiIterable<>(
				List.of(List.of(1), List.of(2)), List::iterator);

		assertEquals(List.of(1, 2), collect(iterable));
		assertEquals(List.of(1, 2), collect(iterable));
	}

	private static <T> List<T> collect(final Iterable<T> iterable) {
		final java.util.ArrayList<T> values = new java.util.ArrayList<>();
		iterable.forEach(values::add);
		return values;
	}

	@Test
	void closesCurrentAndParentWhilePreservingReadFailure() {
		final RuntimeException readFailure = new RuntimeException("read");
		final Exception currentCloseFailure = new Exception("current close");
		final Exception parentCloseFailure = new Exception("parent close");
		final AtomicInteger closes = new AtomicInteger();
		final class ParentIterator implements Iterator<Integer>, AutoCloseable {
			private final Iterator<Integer> delegate = List.of(1).iterator();
			@Override public boolean hasNext() { return delegate.hasNext(); }
			@Override public Integer next() { return delegate.next(); }
			@Override public void close() throws Exception {
				closes.incrementAndGet();
				throw parentCloseFailure;
			}
		}
		final class CurrentIterator implements Iterator<String>, AutoCloseable {
			@Override public boolean hasNext() { throw readFailure; }
			@Override public String next() { throw new AssertionError("next must not be called"); }
			@Override public void close() throws Exception {
				closes.incrementAndGet();
				throw currentCloseFailure;
			}
		}
		final MultiIterator<Integer, String> iterator = new MultiIterator<>(new ParentIterator(),
				value -> new CurrentIterator());

		final RuntimeException thrown = assertThrows(RuntimeException.class, iterator::hasNext);

		assertSame(readFailure, thrown);
		assertEquals(2, closes.get());
		assertEquals(2, thrown.getSuppressed().length);
		assertSame(currentCloseFailure, thrown.getSuppressed()[0]);
		assertSame(parentCloseFailure, thrown.getSuppressed()[1]);
		iterator.close();
		assertEquals(2, closes.get());
	}
}
