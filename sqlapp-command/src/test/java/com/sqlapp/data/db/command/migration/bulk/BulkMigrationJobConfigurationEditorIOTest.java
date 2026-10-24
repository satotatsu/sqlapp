/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Offline file IO only; no JDBC connection is created. */
class BulkMigrationJobConfigurationEditorIOTest {
	@TempDir
	Path directory;
	private final BulkMigrationJobConfigurationEditorIO io = new BulkMigrationJobConfigurationEditorIO();
	private final String yaml = "# 移行ジョブ\r\nschemaFile: schema.xml\r\ntasks:\r\n  - id: items\r\n    table: PUBLIC.ITEMS\r\n";

	@Test
	void preservesUtf8CommentsWhitespaceAndFingerprintAcrossEdits() throws Exception {
		final Path file = directory.resolve("job.yaml");
		final var saved = io.write(file, yaml, null);
		assertEquals(saved, io.read(file));
		assertArrayEquals(yaml.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(file));
		final var edited = io.write(file, yaml + "# edited\n", saved.fingerprint());
		assertNotEquals(saved.fingerprint(), edited.fingerprint());
		assertEquals(edited, io.read(file));
		assertEquals("PUBLIC.ITEMS", io.validate(edited.text()).getTasks().getFirst().getTable());
	}

	@Test
	void rejectsExternalEditsDeletionAndAccidentalOverwrite() throws Exception {
		final Path file = directory.resolve("job.yaml");
		final var opened = io.write(file, yaml, null);
		Files.writeString(file, yaml + "# external\n");
		assertThrows(IOException.class, () -> io.write(file, yaml, opened.fingerprint()));
		assertTrue(Files.readString(file).endsWith("# external\n"));
		assertThrows(IOException.class, () -> io.write(file, yaml, null));
		Files.delete(file);
		assertThrows(IOException.class, () -> io.write(file, yaml, opened.fingerprint()));
		assertFalse(Files.exists(file));
	}

	@Test
	void readsBrokenYamlForRepairButNeverSavesInvalidText() throws Exception {
		final Path file = directory.resolve("broken.yml");
		Files.writeString(file, "tasks: [broken");
		final var opened = io.read(file);
		assertEquals("tasks: [broken", opened.text());
		assertThrows(IllegalArgumentException.class, () -> io.write(file, opened.text(), opened.fingerprint()));
		assertEquals(opened, io.read(file));
	}

	@Test
	void syntaxCheckRejectsUnknownFieldsDuplicatesDocumentsAndTypes() {
		for (final String text : List.of("unknownSetting: hidden", "schemaFile: schema.xml\nschemaFile: other.xml",
				"schemaFile: schema.xml\n---\nschemaFile: other.xml", "null", "[]", "tasks: [{chunkSize: invalid}]",
				"tasks: [{mode: UNKNOWN}]", "tasks: [{resume: null}]")) {
			final var failure = assertThrows(IllegalArgumentException.class, () -> io.validate(text));
			assertFalse(failure.getMessage().contains("hidden"));
		}
	}

	@Test
	void exposesParserLocationWithoutEchoingConfigurationText() {
		final var failure = assertThrows(BulkMigrationJobConfigurationEditorIO.ValidationException.class,
				() -> io.validate("schemaFile: schema.xml\ntasks: [\n"));
		assertTrue(failure.getLine() >= 2);
		assertTrue(failure.getColumn() >= 1);
		assertInstanceOf(IllegalArgumentException.class, failure);
		assertFalse(failure.getMessage().contains("schema.xml"));
		assertNull(failure.getCause());
	}

	@Test
	void syntaxCheckDoesNotPretendToResolveSchemaOrApproveExecution() {
		final var configuration = io.validate("schemaFile: missing.xml\ntasks: []\n");
		assertEquals("missing.xml", configuration.getSchemaFile());
		assertTrue(configuration.getTasks().isEmpty());
		assertFalse(Files.exists(directory.resolve("missing.xml")));
	}

	@Test
	void boundsFilesAndRejectsInvalidUnicodeBeforeWriting() throws Exception {
		final Path file = directory.resolve("job.yaml");
		Files.writeString(file, "x".repeat(1_048_577));
		assertThrows(IOException.class, () -> io.read(file));
		assertThrows(IllegalArgumentException.class, () -> io.validate("x".repeat(1_048_577)));
		Files.write(file, new byte[] { (byte) 0xc3, 0x28 });
		assertThrows(IOException.class, () -> io.read(file));
		assertThrows(IllegalArgumentException.class, () -> io.validate("schemaFile: \ud800"));
	}

	@Test
	void rejectsWrongExtensionAndInvalidExpectedFingerprint() {
		assertThrows(IllegalArgumentException.class, () -> io.write(directory.resolve("schema.xml"), yaml, null));
		assertThrows(IllegalArgumentException.class, () -> io.write(directory.resolve("job.yaml"), yaml, "wrong"));
		assertFalse(Files.exists(directory.resolve("schema.xml")));
	}
}
