/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicMigrationFileTest {
	@TempDir
	Path directory;

	@Test
	void replacesCompletedFileAndRemovesTemporaryFile() throws IOException {
		final Path file = directory.resolve("report.json");
		Files.writeString(file, "old", StandardCharsets.UTF_8);

		AtomicMigrationFile.write(file,
				temporary -> Files.writeString(temporary, "new", StandardCharsets.UTF_8));

		assertEquals("new", Files.readString(file, StandardCharsets.UTF_8));
		assertNoTemporaryFile();
	}

	@Test
	void preservesExistingFileAndRemovesTemporaryFileWhenWriteFails() throws IOException {
		final Path file = directory.resolve("report.json");
		Files.writeString(file, "old", StandardCharsets.UTF_8);

		assertThrows(IOException.class, () -> AtomicMigrationFile.write(file, temporary -> {
			Files.writeString(temporary, "partial", StandardCharsets.UTF_8);
			throw new IOException("write failed");
		}));

		assertEquals("old", Files.readString(file, StandardCharsets.UTF_8));
		assertNoTemporaryFile();
	}

	private void assertNoTemporaryFile() throws IOException {
		try (var files = Files.list(directory)) {
			assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
		}
	}
}
