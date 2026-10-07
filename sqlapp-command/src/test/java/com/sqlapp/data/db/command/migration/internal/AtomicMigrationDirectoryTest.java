/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicMigrationDirectoryTest {
	@TempDir
	Path directory;

	@Test
	void publishesCompleteNewDirectoryAndCleansFailedStaging() throws Exception {
		final Path output = directory.resolve("nested/package");
		AtomicMigrationDirectory.writeNew(output, staging -> {
			Files.writeString(staging.resolve("one.json"), "one");
			Files.writeString(staging.resolve("two.json"), "two");
		});

		assertEquals("one", Files.readString(output.resolve("one.json")));
		assertEquals("two", Files.readString(output.resolve("two.json")));
		assertThrows(IOException.class, () -> AtomicMigrationDirectory.writeNew(output, staging -> {
		}));

		final Path failed = directory.resolve("failed");
		assertThrows(IOException.class, () -> AtomicMigrationDirectory.writeNew(failed, staging -> {
			Files.writeString(staging.resolve("partial.json"), "partial");
			throw new IOException("expected");
		}));
		assertFalse(Files.exists(failed));
		try (var children = Files.list(directory)) {
			assertFalse(children.anyMatch(path -> path.getFileName().toString().startsWith(".failed-")));
		}
	}
}
