/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TransferFileExportServiceTest {
	@TempDir
	Path directory;

	@Test
	void exportsWithOnlySourceBindingPreservingNullEmptyWhitespaceUnicodeAndExactRowCount() throws Exception {
		final var source = database();
		try (var connection = source.getConnection(); var statement = connection.createStatement()) {
			statement.execute(
					"CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY, NAME VARCHAR(100), AMOUNT DECIMAL(10,2))");
			try (var insert = connection.prepareStatement("INSERT INTO ITEMS VALUES(?,?,?)")) {
				final String[] values = { null, "", " 文, \"字\"\r\n line " };
				for (int index = 0; index < values.length; index++) {
					insert.setInt(1, index + 1);
					insert.setString(2, values[index]);
					insert.setBigDecimal(3, new java.math.BigDecimal("1.25"));
					insert.executeUpdate();
				}
			}
		}
		final Path job = job("CREATE_MISSING", "\\N", "UTF-8");
		final var exported = new TransferFileExportService().export(job, Map.of("from", source));
		assertEquals(exported, new TransferFilePackageIO().read(exported.file(), exported.fingerprint()));
		final var table = exported.manifest().tables().getFirst();
		assertEquals(3, table.rows());
		assertEquals(java.util.List.of("ID", "NAME", "AMOUNT"), table.columns());
		final var settings = new com.univocity.parsers.csv.CsvParserSettings();
		settings.setEmptyValue("");
		settings.setNormalizeLineEndingsWithinQuotes(false);
		settings.setIgnoreLeadingWhitespaces(false);
		settings.setIgnoreTrailingWhitespaces(false);
		final java.util.List<String[]> rows;
		try (var reader = Files.newBufferedReader(exported.file().getParent().resolve(table.file()))) {
			rows = new com.univocity.parsers.csv.CsvParser(settings).parseAll(reader);
		}
		assertEquals(4, rows.size());
		assertEquals("\\N", rows.get(1)[1]);
		assertEquals("", rows.get(2)[1]);
		assertEquals(" 文, \"字\"\r\n line ", rows.get(3)[1]);
		assertEquals("1.25", rows.get(3)[2]);
		final String schema = Files.readString(exported.file().getParent().resolve("source-schema.xml"));
		assertFalse(schema.contains("<row>"));
		assertFalse(Files.readString(exported.file()).contains("jdbc:"));
		assertFalse(source.closed);
		final Path moved = directory.resolve("moved");
		Files.move(exported.file().getParent(), moved);
		assertEquals(exported.manifest(),
				new TransferFilePackageIO().read(moved.resolve("manifest.json"), exported.fingerprint()).manifest());
		try (var connection = source.getConnection();
				var statement = connection.createStatement();
				var count = statement.executeQuery("SELECT COUNT(*) FROM ITEMS")) {
			count.next();
			assertEquals(3, count.getInt(1));
		}
	}

	@Test
	void nullTokenCollisionOrEncodingLossDoesNotPublishCompletedPackage() throws Exception {
		for (String value : new String[] { "\\N", "日本語" }) {
			final var source = database();
			try (var connection = source.getConnection(); var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY, NAME VARCHAR(100))");
				try (var insert = connection.prepareStatement("INSERT INTO ITEMS VALUES(1,?)")) {
					insert.setString(1, value);
					insert.executeUpdate();
				}
			}
			assertThrows(IllegalArgumentException.class,
					() -> new TransferFileExportService().export(
							job("REQUIRE_EXISTING", "\\N", value.equals("\\N") ? "UTF-8" : "US-ASCII"),
							Map.of("from", source)));
		}
		try (var paths = Files.walk(directory)) {
			assertFalse(paths.anyMatch(path -> path.getFileName().toString().equals("manifest.json")));
		}
	}

	@Test
	void modifiedOrMissingMembersAndChangedManifestAreRejected() throws Exception {
		for (String member : new String[] { "table-1.csv", "source-schema.xml", "transfer.yaml", "manifest.json" }) {
			final var source = database();
			try (var connection = source.getConnection(); var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)");
			}
			final var exported = new TransferFileExportService().export(job("REQUIRE_EXISTING", "\\N", "UTF-8"),
					Map.of("from", source));
			assertEquals(0, exported.manifest().tables().getFirst().rows());
			final Path changed = exported.file().getParent().resolve(member);
			final String original = Files.readString(changed);
			Files.writeString(changed, member.equals("table-1.csv") ? original.replace("ID", "IX") : original + " ");
			assertThrows(IllegalArgumentException.class,
					() -> new TransferFilePackageIO().read(exported.file(), exported.fingerprint()), member);
			Files.delete(changed);
			assertThrows(Exception.class,
					() -> new TransferFilePackageIO().read(exported.file(), exported.fingerprint()), member);
		}
	}

	@Test
	void rejectsTraversalAndDuplicateDescriptorsWithoutTouchingFilesOutsidePackage() {
		assertThrows(IllegalArgumentException.class, () -> new TransferFilePackageIO.TableFile("task", null, "PUBLIC",
				"ITEMS", java.util.List.of("ID"), "../outside.csv", 0, 0, "sha256:" + "a".repeat(64)));
		final var table = new TransferFilePackageIO.TableFile("task", null, "PUBLIC", "ITEMS", java.util.List.of("ID"),
				"table-1.csv", 0, 0, "sha256:" + "a".repeat(64));
		assertThrows(IllegalArgumentException.class,
				() -> new TransferFilePackageIO.Manifest(1, "sha256:" + "a".repeat(64), "sha256:" + "b".repeat(64),
						new TransferJobConfiguration.FileTransfer("packages", null, null, "\\N"),
						java.util.List.of(table, table)));
	}

	@Test
	void missingRowCountCannotDefaultToZeroInAnOtherwiseValidManifest() throws Exception {
		final var source = database();
		try (var connection = source.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)");
		}
		final var exported = new TransferFileExportService().export(job("REQUIRE_EXISTING", "\\N", "UTF-8"),
				Map.of("from", source));
		final String original = Files.readString(exported.file());
		final String changed = original.replaceFirst("\"rows\"\\s*:\\s*0\\s*,", "");
		assertNotEquals(original, changed);
		Files.writeString(exported.file(), changed);
		assertThrows(IllegalArgumentException.class, () -> new TransferFilePackageIO().read(exported.file(), null));
	}

	@Test
	void unsupportedTemporalValuesDoNotPublishCompletedPackage() throws Exception {
		final var source = database();
		try (var connection = source.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY, DAY_VALUE TIME(6))");
			statement.execute("INSERT INTO ITEMS VALUES(1,TIME '12:34:56.123456')");
		}
		assertThrows(IllegalArgumentException.class,
				() -> new TransferFileExportService().export(Files.writeString(job("REQUIRE_EXISTING", "\\N", "UTF-8"),
						Files.readString(directory.resolve("transfer.yaml")) + "  unsupportedTemporalPolicy: ERROR\n"),
						Map.of("from", source)));
		try (var paths = Files.walk(directory)) {
			assertFalse(paths.anyMatch(path -> path.getFileName().toString().equals("manifest.json")));
		}
	}

	@Test
	void defaultWarningExportsFractionalTimeAndOffsetsAsDriverTextWithoutDroppingPrecision() throws Exception {
		final var source = database();
		try (var connection = source.getConnection(); var statement = connection.createStatement()) {
			statement.execute(
					"CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY, T TIME(6), TS TIMESTAMP(9) WITH TIME ZONE)");
			statement.execute(
					"INSERT INTO ITEMS VALUES(1,TIME '12:34:56.123456',TIMESTAMP '2024-02-29 12:34:56.123456789+09:00')");
		}
		final var exported = new TransferFileExportService().export(job("REQUIRE_EXISTING", "\\N", "UTF-8"),
				Map.of("from", source));
		assertEquals(2, exported.manifest().warnings().size());
		final var csv = Files
				.readString(exported.file().getParent().resolve(exported.manifest().tables().getFirst().file()));
		assertTrue(csv.contains("12:34:56.123456"));
		assertTrue(csv.contains(".123456789+9:00") || csv.contains(".123456789+09:00"), csv);
		assertEquals(exported, new TransferFilePackageIO().read(exported.file(), exported.fingerprint()));
		assertThrows(Exception.class,
				() -> new TransferFileImportService().validatePackage(exported.file(), exported.fingerprint()));
	}

	private Path job(String mode, String nullValue, String encoding) throws Exception {
		return Files.writeString(directory.resolve("transfer.yaml"),
				"formatVersion: 1\nsource: from\ntarget: unused-target\n" + "tables: [ITEMS]\nschemaMode: " + mode
						+ "\ndataMode: FILE\nfiles:\n  directory: packages\n" + "  nullValue: '" + nullValue
						+ "'\n  encoding: " + encoding + "\n");
	}

	private static final class Source extends JDBCDataSource implements AutoCloseable {
		boolean closed;

		@Override
		public void close() {
			closed = true;
		}
	}

	private static Source database() {
		final var source = new Source();
		source.setUrl("jdbc:hsqldb:mem:transfer_export_" + UUID.randomUUID());
		source.setUser("SA");
		source.setPassword("");
		return source;
	}
}
