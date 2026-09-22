/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.rowiterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Iterator;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;

class CsvRowIteratorLifecycleTest {

	@Test
	void closesSuppliedReaderOnceAfterInitialization() throws Exception {
		final TrackingReader reader = new TrackingReader("ID\n1\n", false);
		final Iterator<Row> iterator = new CsvRowIteratorHandler(reader, DataFormat.CSV)
				.iterator(new Table("ITEMS").getRows());

		assertTrue(iterator.hasNext());
		((AutoCloseable) iterator).close();
		((AutoCloseable) iterator).close();

		assertEquals(1, reader.closeCount);
	}

	@Test
	void propagatesSuppliedReaderCloseFailureBeforeInitialization() {
		final TrackingReader reader = new TrackingReader("ID\n1\n", true);
		final Iterator<Row> iterator = new CsvRowIteratorHandler(reader, DataFormat.CSV)
				.iterator(new Table("ITEMS").getRows());

		final IllegalStateException thrown = assertThrows(IllegalStateException.class,
				() -> ((AutoCloseable) iterator).close());

		assertSame(reader.failure, thrown.getCause());
		assertEquals(1, reader.closeCount);
	}

	private static final class TrackingReader extends Reader {
		private final StringReader delegate;
		private final IOException failure = new IOException("close failed");
		private final boolean failOnClose;
		private int closeCount;

		private TrackingReader(final String value, final boolean failOnClose) {
			this.delegate = new StringReader(value);
			this.failOnClose = failOnClose;
		}

		@Override
		public int read(final char[] buffer, final int offset, final int length) throws IOException {
			return delegate.read(buffer, offset, length);
		}

		@Override
		public void close() throws IOException {
			closeCount++;
			if (failOnClose) {
				throw failure;
			}
			delegate.close();
		}
	}
}
