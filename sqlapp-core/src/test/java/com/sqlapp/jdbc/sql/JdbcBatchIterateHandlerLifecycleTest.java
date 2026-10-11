/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.sqlapp.AbstractDbTest;
import com.sqlapp.jdbc.sql.node.SqlNode;

class JdbcBatchIterateHandlerLifecycleTest extends AbstractDbTest {
	@Test
	void closesInputAfterSuccessfulEmptyIteration() throws Exception {
		testDb(connection -> {
			final ClosingIterator iterator = new ClosingIterator(null, null);
			try (var handler = new JdbcBatchIterateHander(new SqlNode("SELECT 1"), 1)) {
				assertEquals(0, handler.execute(connection, () -> iterator));
			}
			assertEquals(1, iterator.closes);
		});
	}

	@Test
	void preservesIterationFailureAndRollsBackBeforeClosing() throws Exception {
		testDb(connection -> {
			final var readFailure = new IllegalArgumentException("invalid input");
			final var closeFailure = new IllegalStateException("close failed");
			final ClosingIterator iterator = new ClosingIterator(readFailure, closeFailure);
			final AtomicInteger rollbacks = new AtomicInteger();
			try (var handler = new JdbcBatchIterateHander(new SqlNode("SELECT 1"), 1)) {
				handler.setRollbackHandler(conn -> rollbacks.incrementAndGet());
				final var thrown = assertThrows(IllegalArgumentException.class,
						() -> handler.execute(connection, () -> iterator));
				assertSame(readFailure, thrown);
				assertArrayEquals(new Throwable[] { closeFailure }, thrown.getSuppressed());
			}
			assertEquals(1, rollbacks.get());
			assertEquals(1, iterator.closes);
		});
	}

	@Test
	void reportsCloseFailureAfterSuccessfulIteration() throws Exception {
		testDb(connection -> {
			final var closeFailure = new IllegalStateException("close failed");
			final ClosingIterator iterator = new ClosingIterator(null, closeFailure);
			try (var handler = new JdbcBatchIterateHander(new SqlNode("SELECT 1"), 1)) {
				final var thrown = assertThrows(java.sql.SQLException.class,
						() -> handler.execute(connection, () -> iterator));
				assertSame(closeFailure, thrown.getCause());
			}
			assertEquals(1, iterator.closes);
		});
	}

	private static final class ClosingIterator implements Iterator<Object>, AutoCloseable {
		private final RuntimeException readFailure;
		private final RuntimeException closeFailure;
		private int closes;

		private ClosingIterator(final RuntimeException readFailure, final RuntimeException closeFailure) {
			this.readFailure = readFailure;
			this.closeFailure = closeFailure;
		}

		@Override
		public boolean hasNext() {
			if (readFailure != null) {
				throw readFailure;
			}
			return false;
		}

		@Override
		public Object next() {
			throw new java.util.NoSuchElementException();
		}

		@Override
		public void close() {
			closes++;
			if (closeFailure != null) {
				throw closeFailure;
			}
		}
	}
}
