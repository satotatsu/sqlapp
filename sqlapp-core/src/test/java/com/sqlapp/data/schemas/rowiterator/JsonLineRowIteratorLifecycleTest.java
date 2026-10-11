/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.rowiterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.Reader;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.JsonConverter;

class JsonLineRowIteratorLifecycleTest {

	@Test
	void propagatesCloseFailureAndClosesOnce() throws Exception {
		final IOException failure = new IOException("close failed");
		final TrackingReader reader = new TrackingReader(failure);
		final var iterator = new JsonLineRowIteratorHandler.JsonlineRowIterator(
				new Table("ITEMS").getRows(), reader, new JsonConverter(), 0L, (row, column, value) -> value);

		final IllegalStateException thrown = assertThrows(IllegalStateException.class, iterator::close);
		iterator.close();

		assertSame(failure, thrown.getCause());
		assertEquals(1, reader.closeCount);
	}

	private static final class TrackingReader extends Reader {
		private final IOException failure;
		private int closeCount;

		private TrackingReader(final IOException failure) {
			this.failure = failure;
		}

		@Override
		public int read(final char[] buffer, final int offset, final int length) {
			return -1;
		}

		@Override
		public void close() throws IOException {
			closeCount++;
			throw failure;
		}
	}
}
