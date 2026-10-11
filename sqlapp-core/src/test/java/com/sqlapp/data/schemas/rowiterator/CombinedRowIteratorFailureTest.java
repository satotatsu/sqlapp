/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.rowiterator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;

class CombinedRowIteratorFailureTest {
	@Test
	void closesCreatedIteratorsWhenLaterHandlerCreationFails() {
		final var failure = new IllegalArgumentException("cannot create third iterator");
		final ProbeIterator first = new ProbeIterator(null, null, true);
		final ProbeIterator second = new ProbeIterator(null, null, true);
		final var handler = new CombinedRowIteratorHandler(collection -> first, collection -> second,
				collection -> { throw failure; });
		final var thrown = assertThrows(IllegalArgumentException.class,
				() -> handler.iterator(new Table().getRows()));
		assertSame(failure, thrown);
		assertArrayEquals(new Throwable[] { first.closeFailure, second.closeFailure }, thrown.getSuppressed());
		assertEquals(1, first.closes);
		assertEquals(1, second.closes);
	}

	@Test
	void rejectsANullIteratorAfterClosingEarlierOnes() {
		final ProbeIterator first = new ProbeIterator(null, null, false);
		final var handler = new CombinedRowIteratorHandler(collection -> first, collection -> null);
		final var thrown = assertThrows(NullPointerException.class,
				() -> handler.iterator(new Table().getRows()));
		assertTrue(thrown.getMessage().contains("returned null"));
		assertEquals(1, first.closes);
	}

	@Test
	void doesNotProbeTheCurrentIteratorTwice() {
		final ProbeIterator first = new ProbeIterator(new Table().newRow(), null, false);
		final ProbeIterator second = new ProbeIterator(new Table().newRow(), null, false);
		final var combined = combined(first, second);
		assertTrue(combined.hasNext());
		assertTrue(combined.hasNext());
		assertNotNull(combined.next());
		assertEquals(1, first.hasNextCalls);
		assertTrue(combined.hasNext());
		assertNotNull(combined.next());
		assertFalse(combined.hasNext());
		assertEquals(1, first.closes);
		assertEquals(1, second.closes);
		assertThrows(NoSuchElementException.class, combined::next);
	}

	@Test
	void closesEveryIteratorAndPreservesReadFailure() throws Exception {
		final var failure = new IllegalArgumentException("invalid second file");
		final ProbeIterator first = new ProbeIterator(null, null, false);
		final ProbeIterator second = new ProbeIterator(null, failure, true);
		final ProbeIterator third = new ProbeIterator(new Table().newRow(), null, true);
		final var combined = combined(first, second, third);
		final var thrown = assertThrows(IllegalArgumentException.class, combined::hasNext);
		assertSame(failure, thrown);
		assertEquals(1, first.closes);
		assertEquals(1, second.closes);
		assertEquals(1, third.closes);
		assertEquals(1, thrown.getSuppressed().length);
		assertEquals(1, thrown.getSuppressed()[0].getSuppressed().length);
		assertFalse(combined.hasNext());
	}

	@Test
	void closesAllIteratorsAndCombinesCloseFailures() throws Exception {
		final ProbeIterator first = new ProbeIterator(null, null, true);
		final ProbeIterator second = new ProbeIterator(null, null, true);
		final var combined = combined(first, second);
		final Exception thrown = assertThrows(Exception.class, combined::close);
		assertSame(first.closeFailure, thrown);
		assertArrayEquals(new Throwable[] { second.closeFailure }, thrown.getSuppressed());
		combined.close();
		assertEquals(1, first.closes);
		assertEquals(1, second.closes);
	}

	@Test
	void closeDiscardsAPreparedRow() throws Exception {
		final ProbeIterator iterator = new ProbeIterator(new Table().newRow(), null, false);
		final var combined = combined(iterator);
		assertTrue(combined.hasNext());
		combined.close();
		assertFalse(combined.hasNext());
		assertThrows(NoSuchElementException.class, combined::next);
		assertEquals(1, iterator.closes);
	}

	@SafeVarargs
	private static CombinedRowIteratorHandler.CombinedRowIterator combined(final Iterator<Row>... iterators) {
		return new CombinedRowIteratorHandler.CombinedRowIterator(new Table().getRows(), List.of(iterators));
	}

	private static final class ProbeIterator implements Iterator<Row>, AutoCloseable {
		private final Row row;
		private final RuntimeException readFailure;
		private final RuntimeException closeFailure = new IllegalStateException("close failed");
		private final boolean failClose;
		private int hasNextCalls;
		private int closes;

		private ProbeIterator(final Row row, final RuntimeException readFailure, final boolean failClose) {
			this.row = row;
			this.readFailure = readFailure;
			this.failClose = failClose;
		}

		@Override
		public boolean hasNext() {
			hasNextCalls++;
			if (readFailure != null) {
				throw readFailure;
			}
			return row != null && hasNextCalls == 1;
		}

		@Override
		public Row next() {
			return row;
		}

		@Override
		public void close() {
			closes++;
			if (failClose) {
				throw closeFailure;
			}
		}
	}
}
