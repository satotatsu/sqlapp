/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.iterable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
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
