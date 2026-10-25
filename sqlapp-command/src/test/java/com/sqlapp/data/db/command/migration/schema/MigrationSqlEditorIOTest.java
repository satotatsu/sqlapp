/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.schema;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigrationSqlEditorIOTest {
	@TempDir
	Path directory;
	private final MigrationSqlEditorIO io = new MigrationSqlEditorIO();

	@Test
	void preservesSqlCommentsUnicodeAndLineEndingsAndChecksVersionedDirectory() throws Exception {
		final Path file = directory.resolve("1_customers.sql");
		final String text = "-- 顧客\r\nCREATE TABLE CUSTOMERS(ID INT);\r\n";
		final var saved = io.write(file, text, null);
		assertEquals(text, io.read(file).text());
		assertEquals(saved, io.read(file));
		assertEquals(directory, io.migrationDirectory(saved));
		final var blank = io.write(file, "", saved.fingerprint());
		assertTrue(Files.readString(file).isEmpty());
		assertEquals("", blank.text());
	}

	@Test
	void neverOverwritesExistingOrExternallyChangedFilesWithoutOpenedFingerprint() throws Exception {
		final Path file = directory.resolve("1_customers.sql");
		final var saved = io.write(file, "original", null);
		assertThrows(IOException.class, () -> io.write(file, "overwrite", null));
		Files.writeString(file, "external");
		assertThrows(IOException.class, () -> io.write(file, "overwrite", saved.fingerprint()));
		assertThrows(IOException.class, () -> io.migrationDirectory(saved));
		assertEquals("external", Files.readString(file));
		final Path alias = directory.resolve("2_alias.sql");
		Files.createLink(alias, file);
		assertThrows(IOException.class, () -> io.write(alias, "overwrite", null));
	}

	@Test
	void rejectsUnsupportedTextAndNamesAndUsesSharedDuplicateDetection() throws Exception {
		assertThrows(IllegalArgumentException.class, () -> io.write(directory.resolve("file.txt"), "sql", null));
		assertThrows(IllegalArgumentException.class, () -> io.write(directory.resolve("file.SQL"), "sql", null));
		assertThrows(IllegalArgumentException.class,
				() -> io.write(directory.resolve("1_large.sql"), "a".repeat(1_048_577), null));
		assertThrows(Exception.class, () -> io.write(directory.resolve("1_bad.sql"), "\uD800", null));
		final Path bad = Files.write(directory.resolve("invalid.sql"), new byte[] { (byte) 0xff });
		assertThrows(IOException.class, () -> io.read(bad));
		final var plain = io.write(directory.resolve("draft.sql"), "-- draft", null);
		assertThrows(IllegalArgumentException.class, () -> io.migrationDirectory(plain));
		final var first = io.write(directory.resolve("1_first.sql"), "SELECT 1;", null);
		io.write(directory.resolve("1_duplicate.sql"), "SELECT 2;", null);
		assertThrows(RuntimeException.class, () -> io.migrationDirectory(first));
	}
}
