/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Transport and Schema policy are independent; these tests perform no database
 * IO.
 */
class TransferJobTransportTest {
	@TempDir
	Path directory;
	private final TransferJobConfigurationIO io = new TransferJobConfigurationIO();
	private static final String BASE = "formatVersion: 1\nsource: from\ntarget: to\ntables: [PUBLIC.ITEMS]\n";

	@ParameterizedTest
	@EnumSource(TransferJobConfiguration.SchemaMode.class)
	void schemaPoliciesRoundTripWithCompatibleIntentsWithoutDuplicatingProductPairing(
			TransferJobConfiguration.SchemaMode policy) {
		final String intent = BASE + "schemaMode: " + policy + "\n";
		if (policy == TransferJobConfiguration.SchemaMode.APPLY_DIFFERENCES) {
			assertThrows(IllegalArgumentException.class, () -> io.validate(intent));
			assertThrows(IllegalArgumentException.class,
					() -> io.validate(intent + "dataMode: FILE\nfiles: {directory: packages, nullValue: NULL}\n"));
			final var schemaOnly = io.validate(intent + "dataMode: SCHEMA_ONLY\n");
			assertEquals(policy, schemaOnly.schemaMode());
			assertEquals(schemaOnly, io.validate(io.format(schemaOnly)));
			return;
		}
		final var direct = io.validate(intent);
		assertEquals(TransferJobConfiguration.DataMode.JDBC_DIRECT, direct.dataMode());
		assertNull(direct.files());
		assertEquals(direct, io.validate(io.format(direct)));
		final var file = io.validate(intent + "dataMode: FILE\nfiles:\n  directory: packages\n  nullValue: '\\N'\n");
		assertEquals(TransferJobConfiguration.DataMode.FILE, file.dataMode());
		assertEquals(policy, file.schemaMode());
		assertEquals(TransferJobConfiguration.FileFormat.CSV, file.files().format());
		assertEquals("UTF-8", file.files().encoding());
		assertEquals("\\N", file.files().nullValue());
		assertEquals(file, io.validate(io.format(file)));
		assertThrows(IllegalArgumentException.class, () -> io.validate(intent + "sameProduct: true\n"));
	}

	@Test
	void jsonlUsesJsonNullAndFilePathFollowsTheJobDirectory() throws Exception {
		final String yaml = BASE + "schemaMode: REQUIRE_EXISTING\ndataMode: FILE\nfiles:\n"
				+ "  directory: packages/run-1\n  format: JSONL\n  encoding: UTF8\n";
		final var saved = io.write(directory.resolve("original/transfer.yaml"), yaml, null);
		assertEquals(directory.resolve("original/packages/run-1"), saved.filesDirectory());
		assertEquals("UTF-8", saved.configuration().files().encoding());
		assertNull(saved.configuration().files().nullValue());
		Files.move(directory.resolve("original"), directory.resolve("moved"));
		final var moved = io.read(directory.resolve("moved/transfer.yaml"));
		assertEquals(directory.resolve("moved/packages/run-1"), moved.filesDirectory());
		assertEquals(saved.fingerprint(), moved.fingerprint());
		assertEquals(yaml, moved.text());
		assertFalse(Files.exists(moved.filesDirectory()));
	}

	@Test
	void rejectsConflictingMissingAndUnknownFileSettings() {
		final String file = BASE + "schemaMode: REQUIRE_EXISTING\ndataMode: FILE\n";
		for (String options : List.of("", "files: {}\n", "files: {directory: packages}\n",
				"files: {directory: packages, nullValue: ''}\n",
				"files: {directory: packages, nullValue: ',', format: CSV}\n",
				"files: {directory: packages, nullValue: token, encoding: unknown-encoding}\n",
				"files: {directory: packages, nullValue: 日本, encoding: US-ASCII}\n",
				"files: {directory: packages, nullValue: token, format: XLSX}\n",
				"files: {directory: packages, nullValue: token, format: JSONL}\n",
				"files: {directory: packages, nullValue: token, password: secret}\n")) {
			final var failure = assertThrows(IllegalArgumentException.class, () -> io.validate(file + options),
					options);
			assertFalse(failure.getMessage().contains("secret"));
			assertNull(failure.getCause());
		}
		assertThrows(IllegalArgumentException.class, () -> io.validate(file.replace("FILE", "UNKNOWN")));
		assertThrows(IllegalArgumentException.class, () -> io
				.validate(file.replace("FILE", "JDBC_DIRECT") + "files: {directory: packages, nullValue: token}\n"));
	}

	@Test
	void fileModeCannotFallThroughToDirectCopyOrBorrowConnections() throws Exception {
		final Path job = io.write(directory.resolve("transfer.yaml"),
				BASE + "schemaMode: CREATE_MISSING\n" + "dataMode: FILE\nfiles: {directory: packages, format: JSONL}\n",
				null).file();
		final var opened = new AtomicInteger();
		final var neverOpen = new JDBCDataSource() {
			@Override
			public java.sql.Connection getConnection() throws SQLException {
				opened.incrementAndGet();
				throw new SQLException("A file job must not start direct JDBC execution");
			}
		};
		final var failure = assertThrows(IllegalArgumentException.class, () -> new TransferJobPreparationService()
				.prepare(job, Map.of("from", neverOpen, "to", neverOpen), directory));
		assertTrue(failure.getMessage().contains("separate export/import"));
		assertThrows(IllegalArgumentException.class, () -> new TransferJobExecutionService().review(job,
				Map.of("from", neverOpen, "to", neverOpen), directory));
		assertEquals(0, opened.get());
		assertFalse(Files.exists(directory.resolve("packages")));
	}
}
