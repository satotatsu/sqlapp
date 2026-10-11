/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.util.iterator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Iterator;

import org.junit.jupiter.api.Test;

class IteratorCloseUtilsTest {
	@Test
	void closesDistinctIteratorsOnceAndCombinesFailures() {
		final ClosingIterator first = new ClosingIterator(new IllegalArgumentException("first"));
		final ClosingIterator second = new ClosingIterator(new IllegalStateException("second"));
		final var thrown = assertThrows(IllegalArgumentException.class,
				() -> IteratorCloseUtils.close(null, first, first, second));
		assertSame(first.failure, thrown);
		assertArrayEquals(new Throwable[] { second.failure }, thrown.getSuppressed());
		assertEquals(1, first.closes);
		assertEquals(1, second.closes);
	}

	private static final class ClosingIterator implements Iterator<Object>, AutoCloseable {
		private final RuntimeException failure;
		private int closes;

		private ClosingIterator(final RuntimeException failure) {
			this.failure = failure;
		}

		@Override public boolean hasNext() { return false; }
		@Override public Object next() { throw new java.util.NoSuchElementException(); }
		@Override public void close() { closes++; throw failure; }
	}
}
