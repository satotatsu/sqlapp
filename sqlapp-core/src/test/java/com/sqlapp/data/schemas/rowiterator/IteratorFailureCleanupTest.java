/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.rowiterator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;

class IteratorFailureCleanupTest {
	static Stream<Arguments> failures() {
		return Stream.of(false, true).flatMap(text -> Stream.of("open", "columns", "hasNext", "read", "convert")
				.map(stage -> Arguments.of(text, stage)));
	}

	@ParameterizedTest
	@MethodSource("failures")
	void preservesFailureAndClosesOnce(final boolean text, final String stage) throws Exception {
		final Probe probe = new Probe(stage);
		final AbstractIterator iterator = iterator(text, probe);
		final RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
			if (iterator.hasNext()) {
				iterator.next();
			}
		});
		assertTrue(thrown == probe.failure || thrown.getCause() == probe.failure);
		assertArrayEquals(new Throwable[] { probe.closeFailure }, probe.failure.getSuppressed());
		assertEquals(1, probe.closes);
		assertFalse(iterator.hasNext());
		iterator.close();
		assertEquals(1, probe.closes);
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void exhaustionClosesOnce(final boolean text) throws Exception {
		final Probe probe = new Probe(null);
		final AbstractIterator iterator = iterator(text, probe);
		assertFalse(iterator.hasNext());
		assertFalse(iterator.hasNext());
		iterator.close();
		assertEquals(1, probe.closes);
	}

	private static final class Probe {
		private final String stage;
		private final RuntimeException failure = new IllegalArgumentException("invalid input");
		private final RuntimeException closeFailure = new IllegalStateException("close failed");
		private int closes;
		private Probe(final String stage) { this.stage = stage; }
		private void check(final String current) {
			if (current.equals(stage)) { throw failure; }
		}
		private void close() {
			closes++;
			if (stage != null) { throw closeFailure; }
		}
	}

	private AbstractIterator iterator(final boolean text, final Probe probe) {
		final var rows = new Table("ITEMS").getRows();
		if (text) {
			return new AbstractTextRowListIterator<String>(rows, 0L, (r, c, v) -> v) {
				@Override protected void preInitialize() { probe.check("open"); }
				@Override protected void initializeColumn() { probe.check("columns"); }
				@Override protected boolean hasNextInternal() {
					probe.check("hasNext");
					return probe.stage != null;
				}
				@Override protected String read() { probe.check("read"); return "value"; }
				@Override protected void set(final String value, final Row row) { probe.check("convert"); }
				@Override protected void doClose() { probe.close(); }
			};
		}
		 {
			return new AbstractRowIterator<String>(rows, 0L, (r, c, v) -> v) {
				@Override protected void preInitialize() { probe.check("open"); }
				@Override protected void initializeColumn() { probe.check("columns"); }
				@Override protected boolean hasNextInternal() {
					probe.check("hasNext");
					return probe.stage != null;
				}
				@Override protected String read() { probe.check("read"); return "value"; }
				@Override protected void set(final String value, final Row row) { probe.check("convert"); }
				@Override protected void doClose() { probe.close(); }
			};
		}
	}
}
