/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Offline contract tests: no JDBC driver, connection or database is needed. */
class TransferJobConfigurationIOTest {
	@Test
	void temporalExportDefaultsToWarningsAndAllowsExplicitErrorPolicy() {
		final var io = new TransferJobConfigurationIO();
		final String yaml = "formatVersion: 1\nsource: source\ntarget: target\ntables: [ITEMS]\nschemaMode: REQUIRE_EXISTING\ndataMode: FILE\nfiles: {directory: packages, nullValue: __NULL__}";
		assertEquals(TransferJobConfiguration.TemporalPolicy.WARN,
				io.validate(yaml).files().unsupportedTemporalPolicy());
		final var strict = io
				.validate(yaml.replace("nullValue: __NULL__", "nullValue: __NULL__, unsupportedTemporalPolicy: ERROR"));
		assertEquals(TransferJobConfiguration.TemporalPolicy.ERROR, strict.files().unsupportedTemporalPolicy());
		assertEquals(strict, io.validate(io.format(strict)));
		assertThrows(IllegalArgumentException.class, () -> io.validate(
				yaml.replace("nullValue: __NULL__", "nullValue: __NULL__, unsupportedTemporalPolicy: IGNORE")));
	}

	@Test
	void compatibilityPolicyDefaultsToWarningsAndStrictModeIsExplicit() {
		assertEquals(TransferJobConfiguration.CompatibilityPolicy.WARN, io.validate(yaml).compatibilityPolicy());
		final var strict = io.validate(yaml + "compatibilityPolicy: ERROR\n");
		assertEquals(TransferJobConfiguration.CompatibilityPolicy.ERROR, strict.compatibilityPolicy());
		assertEquals(strict, io.validate(io.format(strict)));
		assertThrows(IllegalArgumentException.class, () -> io.validate(yaml + "compatibilityPolicy: IGNORE\n"));
	}

	@TempDir
	Path directory;
	private final TransferJobConfigurationIO io = new TransferJobConfigurationIO();
	private final String yaml = """
			formatVersion: 1
			source: source-db
			target: target-db
			tables: [PUBLIC.ITEMS]
			schemaMode: CREATE_MISSING
			""";

	@Test
	void minimumIntentHasSafeCopyDefaultsAndModelRoundTrips() throws Exception {
		final var configuration = io.validate(yaml);
		assertEquals(10_000, configuration.chunkSize());
		assertTrue(configuration.verify());
		assertEquals(TransferJobConfiguration.SchemaMode.CREATE_MISSING, configuration.schemaMode());
		assertEquals(configuration, io.validate(io.format(configuration)));
		assertEquals(configuration, new TransferJobConfiguration("source-db", "target-db", List.of("PUBLIC.ITEMS"),
				TransferJobConfiguration.SchemaMode.CREATE_MISSING));
		assertNull(configuration.mappingFile());
		assertNull(configuration.sourceSchema());
		assertNull(configuration.targetSchema());
		final var copy = new ArrayList<>(List.of("PUBLIC.ITEMS"));
		final var immutable = new TransferJobConfiguration(1, "from", "to", copy,
				TransferJobConfiguration.SchemaMode.REQUIRE_EXISTING, null, null, null, null, null);
		copy.clear();
		assertEquals(List.of("PUBLIC.ITEMS"), immutable.tables());
		assertThrows(UnsupportedOperationException.class, () -> immutable.tables().clear());
	}

	@Test
	void preservesCommentsUnicodeAndExactByteFingerprintAcrossEdits() throws Exception {
		final String text = "# 移行設定\r\n" + yaml.replace("\n", "\r\n") + "sourceSchema: 営業\r\n";
		final Path file = directory.resolve("transfer.yaml");
		final var saved = io.write(file, text, null);
		assertEquals(saved, io.read(file));
		assertArrayEquals(text.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(file));
		final var edited = io.write(file, text + "verify: false\r\n", saved.fingerprint());
		assertNotEquals(saved.fingerprint(), edited.fingerprint());
		assertFalse(edited.configuration().verify());
	}

	@Test
	void relativeMappingFollowsJobAfterMovingItsDirectoryWithoutRequiringMappingToExist() throws Exception {
		final var first = io.write(directory.resolve("original/job.yml"), yaml + "mappingFile: mapping/types.yaml\n",
				null);
		assertEquals(directory.resolve("original/mapping/types.yaml"), first.mappingFile());
		Files.move(directory.resolve("original"), directory.resolve("moved"));
		final var moved = io.read(directory.resolve("moved/job.yml"));
		assertEquals(directory.resolve("moved/mapping/types.yaml"), moved.mappingFile());
		assertEquals(first.fingerprint(), moved.fingerprint());
		assertFalse(Files.exists(moved.mappingFile()));
		assertNull(io.write(directory.resolve("no-mapping.yaml"), yaml, null).mappingFile());
	}

	@Test
	void preservesExplicitTargetAndCopyOptions() {
		final var configuration = io.validate(yaml.replace("CREATE_MISSING", "REQUIRE_EXISTING")
				+ "targetSchema: ARCHIVE\nchunkSize: 250\nverify: false\n");
		assertEquals(TransferJobConfiguration.SchemaMode.REQUIRE_EXISTING, configuration.schemaMode());
		assertEquals("ARCHIVE", configuration.targetSchema());
		assertEquals(250, configuration.chunkSize());
		assertFalse(configuration.verify());
	}

	@Test
	void rejectsIncompleteUnsupportedAndAmbiguousIntentBeforeSaving() {
		for (final String text : List.of("null", "[]", "{}", yaml.replace("formatVersion: 1\n", ""),
				yaml.replace("formatVersion: 1", "formatVersion: 2"), yaml.replace("schemaMode: CREATE_MISSING\n", ""),
				yaml.replace("source: source-db\n", ""), yaml.replace("target: target-db\n", ""),
				yaml.replace("CREATE_MISSING", "DROP_AND_RECREATE"), yaml.replace("[PUBLIC.ITEMS]", "[]"),
				yaml.replace("[PUBLIC.ITEMS]", "[PUBLIC.ITEMS, PUBLIC.ITEMS]"),
				yaml.replace("[PUBLIC.ITEMS]", "[null]"), yaml + "chunkSize: 0\n", yaml + "chunkSize: 1000001\n",
				yaml + "sourceSchema: ''\n", yaml + "mappingFile: ''\n")) {
			assertThrows(IllegalArgumentException.class, () -> io.validate(text), text);
		}
	}

	@Test
	void rejectsUnknownDuplicateMultipleDocumentsAndScalarCoercionWithoutLeakingInput() {
		for (final String text : List.of(yaml + "password: super-secret\n", yaml + "source: other\n",
				yaml + "---\n" + yaml, yaml + "chunkSize: '250'\n", yaml + "verify: 'false'\n",
				yaml + "chunkSize: 1.5\n", yaml.replace("CREATE_MISSING", "0"),
				yaml.replace("source-db", "jdbc:vendor://host/password=super-secret"),
				yaml + "tables: [broken-super-secret\n", yaml + "formatVersion: null\n")) {
			final var failure = assertThrows(IllegalArgumentException.class, () -> io.validate(text));
			assertFalse(failure.getMessage().contains("super-secret"));
			assertNull(failure.getCause());
		}
	}

	@Test
	void rejectsOverwriteAndExternalChangesWithoutChangingExistingBytes() throws Exception {
		final Path file = directory.resolve("transfer.yaml");
		final var saved = io.write(file, yaml, null);
		assertThrows(IOException.class, () -> io.write(file, yaml, null));
		Files.writeString(file, yaml + "# external change\n");
		assertThrows(IOException.class, () -> io.write(file, yaml, saved.fingerprint()));
		assertEquals(yaml + "# external change\n", Files.readString(file));
		Files.delete(file);
		assertThrows(IOException.class, () -> io.write(file, yaml, saved.fingerprint()));
		assertFalse(Files.exists(file));
	}

	@Test
	void rejectsOversizedOrInvalidUnicodeAndInvalidSaveInputs() throws Exception {
		final Path file = directory.resolve("transfer.yaml");
		Files.write(file, new byte[] { (byte) 0xc3, 0x28 });
		assertThrows(IOException.class, () -> io.read(file));
		Files.writeString(file, "x".repeat(1_048_577));
		assertThrows(Exception.class, () -> io.read(file));
		assertThrows(IllegalArgumentException.class, () -> io.validate("x".repeat(1_048_577)));
		assertThrows(IllegalArgumentException.class, () -> io.validate(yaml + "# \ud800"));
		assertThrows(IllegalArgumentException.class, () -> io.write(directory.resolve("job.xml"), yaml, null));
		assertThrows(IllegalArgumentException.class, () -> io.write(file, yaml, "invalid"));
		final Path invalid = directory.resolve("invalid.yaml");
		assertThrows(IllegalArgumentException.class, () -> io.write(invalid, yaml + "password: secret\n", null));
		assertFalse(Files.exists(invalid));
	}
}
