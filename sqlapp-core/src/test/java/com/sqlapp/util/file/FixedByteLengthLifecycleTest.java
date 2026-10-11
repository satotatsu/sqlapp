/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.util.file;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class FixedByteLengthLifecycleTest {

	@Test
	void parserPropagatesInputCloseFailure() {
		final IOException failure = new IOException("input close failed");
		final InputStream input = new InputStream() {
			@Override public int read() { return -1; }
			@Override public void close() throws IOException { throw failure; }
		};
		final FixedByteLengthParser parser = new FixedByteLengthParser(input, StandardCharsets.UTF_8);

		final IllegalStateException thrown = assertThrows(IllegalStateException.class, parser::close);

		assertSame(failure, thrown.getCause());
	}

	@Test
	void writerPropagatesOutputCloseFailure() {
		final IOException failure = new IOException("output close failed");
		final OutputStream output = new OutputStream() {
			@Override public void write(final int value) { }
			@Override public void close() throws IOException { throw failure; }
		};
		final FixedByteLengthWriter writer = new FixedByteLengthWriter(output, StandardCharsets.UTF_8);

		final IllegalStateException thrown = assertThrows(IllegalStateException.class, writer::close);

		assertSame(failure, thrown.getCause());
	}
}
