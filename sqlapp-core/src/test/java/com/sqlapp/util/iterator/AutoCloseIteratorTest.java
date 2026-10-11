/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.util.iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class AutoCloseIteratorTest {
	@Test
	void preservesReadFailureAndClosesOnlyOnce() {
		final RuntimeException readFailure = new RuntimeException("read");
		final Exception closeFailure = new Exception("close");
		final AtomicInteger closes = new AtomicInteger();
		final Iterator<Integer> source = new Iterator<>() {
			@Override
			public boolean hasNext() {
				throw readFailure;
			}

			@Override
			public Integer next() {
				throw new AssertionError("next must not be called");
			}
		};
		final class CloseableIterator implements Iterator<Integer>, AutoCloseable {
			@Override public boolean hasNext() { return source.hasNext(); }
			@Override public Integer next() { return source.next(); }
			@Override public void close() throws Exception {
				closes.incrementAndGet();
				throw closeFailure;
			}
		}
		final AutoCloseIterator<Integer> iterator = new AutoCloseIterator<>(new CloseableIterator());

		final RuntimeException thrown = assertThrows(RuntimeException.class, iterator::hasNext);

		assertSame(readFailure, thrown);
		assertEquals(1, closes.get());
		assertEquals(1, thrown.getSuppressed().length);
		assertSame(closeFailure, thrown.getSuppressed()[0]);
		iterator.close();
		assertEquals(1, closes.get());
		assertFalse(iterator.hasNext());
	}
}
