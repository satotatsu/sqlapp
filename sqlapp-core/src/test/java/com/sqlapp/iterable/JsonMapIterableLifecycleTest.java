/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.iterable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

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
	void reportsInputStreamCloseFailure() {
		final IOException closeFailure = new IOException("close");
		final ByteArrayInputStream input = new ByteArrayInputStream("[{\"id\":1}]".getBytes(StandardCharsets.UTF_8)) {
			@Override
			public void close() throws IOException {
				throw closeFailure;
			}
		};

		final RuntimeException thrown = assertThrows(RuntimeException.class,
				() -> new JsonMapIterable(input).iterator().next());
		assertSame(closeFailure, thrown.getCause());
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
				() -> new JsonMapIterable(reader).iterator().next());
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
		assertEquals(1, thrown.getSuppressed().length);
		assertSame(closeFailure, thrown.getSuppressed()[0].getCause());
	}

	@Test
	void readsIncrementallyAndClosesOnEarlyExit() throws Exception {
		final String json = "[" + "{\"id\":1},".repeat(100000) + "{\"id\":2}]";
		final AtomicInteger consumed = new AtomicInteger();
		final AtomicInteger closes = new AtomicInteger();
		final Reader reader = new StringReader(json) {
			@Override
			public int read(char[] buffer, int offset, int length) throws IOException {
				final int count = super.read(buffer, offset, length);
				if (count > 0) {
					consumed.addAndGet(count);
				}
				return count;
			}

			@Override
			public void close() {
				closes.incrementAndGet();
				super.close();
			}
		};
		final var iterator = new JsonMapIterable(reader).iterator();
		assertTrue(iterator.hasNext());
		assertTrue(iterator.hasNext());
		assertEquals(1, iterator.next().get("id"));
		assertTrue(consumed.get() < json.length() / 10);
		assertEquals(0, closes.get());
		((AutoCloseable) iterator).close();
		((AutoCloseable) iterator).close();
		assertEquals(1, closes.get());
		assertFalse(iterator.hasNext());
		assertThrows(NoSuchElementException.class, iterator::next);
	}

	@Test
	void preservesNestedValuesAndClosesAtEnd() {
		final AtomicInteger closes = new AtomicInteger();
		final var iterator = new JsonMapIterable(
				trackingReader("[{\"id\":1,\"nested\":{\"values\":[true,null,2.5]}},{\"id\":2}]", closes)).iterator();
		final var first = iterator.next();
		assertEquals(List.of("id", "nested"), List.copyOf(first.keySet()));
		assertEquals(java.util.Arrays.asList(true, null, 2.5), ((Map<?, ?>) first.get("nested")).get("values"));
		assertEquals(2, iterator.next().get("id"));
		assertFalse(iterator.hasNext());
		assertEquals(1, closes.get());
	}

	@Test
	void rejectsInvalidRowsTruncatedArraysAndTrailingContent() {
		for (String json : List.of("[1]", "[null]", "[[]]", "[{\"id\":1}", "[] {}")) {
			final AtomicInteger closes = new AtomicInteger();
			assertThrows(RuntimeException.class, () -> {
				final var iterator = new JsonMapIterable(trackingReader(json, closes)).iterator();
				while (iterator.hasNext()) {
					iterator.next();
				}
			});
			assertEquals(1, closes.get());
		}
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
