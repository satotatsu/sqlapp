/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.iterable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class JsonMapIterableLifecycleTest {

	@Test
	void closesReaderOnceAfterSuccessfulRead() {
		final AtomicInteger closes = new AtomicInteger();
		final StringReader reader = trackingReader("[{\"id\":1}]", closes);

		assertEquals(1, new JsonMapIterable(reader).iterator().next().get("id"));
		assertEquals(1, closes.get());
	}

	@Test
	void closesReaderOnceWhenJsonIsInvalid() {
		final AtomicInteger closes = new AtomicInteger();
		final StringReader reader = trackingReader("not-json", closes);

		assertThrows(RuntimeException.class, () -> new JsonMapIterable(reader).iterator());
		assertEquals(1, closes.get());
	}

	@Test
	void closesInputStreamOnceAfterSuccessfulRead() {
		final AtomicInteger closes = new AtomicInteger();
		final ByteArrayInputStream input = trackingInputStream("[{\"id\":1}]", closes);

		assertEquals(1, new JsonMapIterable(input).iterator().next().get("id"));
		assertEquals(1, closes.get());
	}

	@Test
	void closesInputStreamOnceWhenJsonIsInvalid() {
		final AtomicInteger closes = new AtomicInteger();
		final ByteArrayInputStream input = trackingInputStream("not-json", closes);

		assertThrows(RuntimeException.class, () -> new JsonMapIterable(input).iterator());
		assertEquals(1, closes.get());
	}

	@Test
	void reportsReaderCloseFailure() {
		final IOException closeFailure = new IOException("close");
		final Reader reader = new Reader() {
			private final StringReader delegate = new StringReader("[{\"id\":1}]");

			@Override
			public int read(final char[] buffer, final int offset, final int length) throws IOException {
				return delegate.read(buffer, offset, length);
			}

			@Override
			public void close() throws IOException {
				throw closeFailure;
			}
		};

		final RuntimeException thrown = assertThrows(RuntimeException.class,
				() -> new JsonMapIterable(reader).iterator());
		assertEquals(closeFailure, thrown.getCause());
	}

	@Test
	void preservesReadFailureAndSuppressesCloseFailure() {
		final IOException readFailure = new IOException("read");
		final IOException closeFailure = new IOException("close");
		final Reader reader = new Reader() {
			@Override
			public int read(final char[] buffer, final int offset, final int length) throws IOException {
				throw readFailure;
			}

			@Override
			public void close() throws IOException {
				throw closeFailure;
			}
		};

		final RuntimeException thrown = assertThrows(RuntimeException.class,
				() -> new JsonMapIterable(reader).iterator());
		assertSame(readFailure, thrown.getCause());
		assertEquals(1, readFailure.getSuppressed().length);
		assertSame(closeFailure, readFailure.getSuppressed()[0]);
	}

	private static StringReader trackingReader(final String value, final AtomicInteger closes) {
		return new StringReader(value) {
			@Override
			public void close() {
				closes.incrementAndGet();
				super.close();
			}
		};
	}

	private static ByteArrayInputStream trackingInputStream(final String value, final AtomicInteger closes) {
		return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)) {
			@Override
			public void close() throws IOException {
				closes.incrementAndGet();
				super.close();
			}
		};
	}
}
