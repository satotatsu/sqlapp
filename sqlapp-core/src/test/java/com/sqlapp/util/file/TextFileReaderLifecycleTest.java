/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.util.file;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.Reader;

import org.junit.jupiter.api.Test;

class TextFileReaderLifecycleTest {

	@Test
	void propagatesReaderCloseFailure() {
		final IOException failure = new IOException("close failed");
		final Reader reader = new Reader() {
			@Override
			public int read(final char[] buffer, final int offset, final int length) {
				return -1;
			}

			@Override
			public void close() throws IOException {
				throw failure;
			}
		};
		final TextFileReader fileReader = new TextFileReader(FileType.CSV, reader, settings -> {
		});

		final IllegalStateException thrown = assertThrows(IllegalStateException.class, fileReader::close);

		assertSame(failure, thrown.getCause());
	}
}
