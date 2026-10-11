/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.iterable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class ExcelIterableLifecycleTest {
	@Test
	void reportsInputCloseFailureAndClosesOnlyOnce() throws Exception {
		final IOException closeFailure = new IOException("close");
		final AtomicInteger closes = new AtomicInteger();
		final ByteArrayInputStream input = new ByteArrayInputStream(new byte[0]) {
			@Override
			public void close() throws IOException {
				closes.incrementAndGet();
				throw closeFailure;
			}
		};
		final ExcelIterable.ExcelIterator iterator = new ExcelIterable.ExcelIterator(input);

		final Exception thrown = assertThrows(Exception.class, iterator::close);

		assertSame(closeFailure, thrown);
		assertEquals(1, closes.get());
		iterator.close();
		assertEquals(1, closes.get());
	}
}
