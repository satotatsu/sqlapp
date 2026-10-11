/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.rowiterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Table;

class XmlRowIteratorLifecycleTest {

	@Test
	void reportsReaderCloseFailureAfterRowsAreRead() {
		final IOException failure = new IOException("close failed");
		final TrackingReader reader = new TrackingReader(
				"<table name=\"ITEMS\"><rows><row><value key=\"ID\">1</value></row></rows></table>", failure);
		final var iterator = new XmlRowIteratorHandler(reader).iterator(new Table("ITEMS").getRows());

		assertTrue(iterator.hasNext());
		iterator.next();
		final IllegalStateException thrown = assertThrows(IllegalStateException.class, iterator::hasNext);

		assertSame(failure, thrown.getCause());
		assertEquals(2, reader.closeCount);
	}

	private static final class TrackingReader extends Reader {
		private final StringReader delegate;
		private final IOException failure;
		private int closeCount;

		private TrackingReader(final String value, final IOException failure) {
			this.delegate = new StringReader(value);
			this.failure = failure;
		}

		@Override
		public int read(final char[] buffer, final int offset, final int length) throws IOException {
			return delegate.read(buffer, offset, length);
		}

		@Override
		public void close() throws IOException {
			closeCount++;
			if (closeCount > 1) {
				throw failure;
			}
			delegate.close();
		}
	}
}
