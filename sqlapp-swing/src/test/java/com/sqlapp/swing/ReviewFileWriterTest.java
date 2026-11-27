/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReviewFileWriterTest {
	@TempDir
	Path directory;

	@Test
	void savesUtf8AndPreservesInputFiles() throws Exception {
		final Path before = directory.resolve("before.xml");
		final Path after = directory.resolve("after.xml");
		Files.writeString(before, "original");
		Files.writeString(after, "target");
		final Path destination = directory.resolve("review.txt");
		final var writer = new ReviewFileWriter();
		writer.write(destination, "変更SQL", before, after);
		assertEquals("変更SQL", Files.readString(destination));
		assertThrows(IllegalArgumentException.class, () -> writer.write(before, "bad", before, after));
		assertThrows(IllegalArgumentException.class, () -> writer.write(after, "bad", before, after));
		assertEquals("original", Files.readString(before));
		assertEquals("target", Files.readString(after));
		try (var files = Files.list(directory)) {
			assertEquals(3L, files.count());
		}
	}
}
