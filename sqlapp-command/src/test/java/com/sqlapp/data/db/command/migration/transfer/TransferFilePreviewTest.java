/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.sqlapp.data.db.command.migration.internal.BoundedMigrationFile;

class TransferFilePreviewTest {
	@TempDir
	Path directory;

	private TransferFilePackageIO.Snapshot exported(String format, String encoding, String header) throws Exception {
		var source = new JDBCDataSource();
		source.setUrl("jdbc:hsqldb:mem:preview_" + UUID.randomUUID());
		source.setUser("SA");
		source.setPassword("");
		try {
			try (var connection = source.getConnection(); var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY, NAME VARCHAR(5000))");
				try (var insert = connection.prepareStatement("INSERT INTO ITEMS VALUES(?,?)")) {
					String[] values = { null, "", "null", " 日本語\t\"文字\"\r\n" + "あ".repeat(600) };
					for (int index = 0; index < values.length; index++) {
						insert.setInt(1, index + 1);
						insert.setString(2, values[index]);
						insert.executeUpdate();
					}
				}
			}
			var job = Files.writeString(directory.resolve("job.yaml"),
					"formatVersion: 1\nsource: from\ntarget: to\ntables: [ITEMS]\nschemaMode: REQUIRE_EXISTING\ndataMode: FILE\nfiles: {directory: packages, format: "
							+ format + ", encoding: " + encoding + ", headerMode: " + header
							+ (format.equals("JSONL") ? "" : ", nullValue: __NULL__") + "}\n");
			return new TransferFileExportService().export(job, Map.of("from", source));
		} finally {
			try (var connection = source.getConnection(); var statement = connection.createStatement()) {
				statement.execute("SHUTDOWN");
			}
		}
	}

	@ParameterizedTest
	@CsvSource({ "CSV, windows-31j, SKIP", "TSV, UTF-16LE, NONE", "JSONL, UTF-8, NONE" })
	void previewsSharedTypedValuesOfflineWithBoundedImmutableEvidence(String format, String encoding, String header)
			throws Exception {
		var saved = exported(format, encoding, header);
		var service = new TransferFileImportService();
		var first = service.preview(saved.file(), saved.fingerprint(), "export-table-1", 2);
		assertEquals(2, first.rows().size());
		assertTrue(first.moreRecordedRows());
		assertEquals(4, first.table().rows());
		assertEquals(saved, first.snapshot());
		assertEquals("1", first.rows().getFirst().getFirst().text());
		assertTrue(first.rows().getFirst().get(1).sqlNull());
		assertEquals("null", first.rows().getFirst().get(1).text());
		assertEquals("\"\"", first.rows().get(1).get(1).text());
		assertThrows(UnsupportedOperationException.class, () -> first.rows().getFirst().clear());
		var all = service.preview(saved.file(), saved.fingerprint(), "export-table-1");
		assertEquals("\"null\"", all.rows().get(2).get(1).text());
		assertTrue(all.rows().get(3).get(1).truncated());
		assertTrue(all.rows().get(3).get(1).toString().contains("[truncated]"));
		assertFalse(all.moreRecordedRows());
		assertThrows(IllegalArgumentException.class,
				() -> service.preview(saved.file(), saved.fingerprint(), "missing", 2));
		assertThrows(IllegalArgumentException.class,
				() -> service.preview(saved.file(), saved.fingerprint(), "export-table-1", 101));
		Files.writeString(saved.file().getParent().resolve(saved.manifest().tables().getFirst().file()), "changed");
		assertThrows(Exception.class, () -> service.preview(saved.file(), saved.fingerprint(), "export-table-1", 2));
	}

	@Test
	void sampleDoesNotClaimToValidateAnUnparsedTail() throws Exception {
		var saved = exported("JSONL", "UTF-8", "NONE");
		var member = saved.manifest().tables().getFirst();
		var file = saved.file().getParent().resolve(member.file());
		var lines = Files.readAllLines(file);
		lines.set(3, "{\"ID\":\"wrong type\",\"NAME\":null}");
		Files.write(file, lines);
		var changed = new TransferFilePackageIO.TableFile(member.taskId(), member.catalog(), member.schema(),
				member.table(), member.columns(), member.file(), member.rows(), Files.size(file),
				BoundedMigrationFile.sha256(file, null, "maxDataBytes", "data"));
		Files.writeString(saved.file(),
				new com.sqlapp.util.JsonConverter().toJsonString(new TransferFilePackageIO.Manifest(1,
						saved.manifest().jobFingerprint(), saved.manifest().schemaFingerprint(),
						saved.manifest().files(), List.of(changed), saved.manifest().warnings())));
		var snapshot = new TransferFilePackageIO().read(saved.file(), null);
		var service = new TransferFileImportService();
		assertEquals(2, service.preview(snapshot.file(), snapshot.fingerprint(), member.taskId(), 2).rows().size());
		assertThrows(Exception.class, () -> service.validatePackage(snapshot.file(), snapshot.fingerprint()));
		assertThrows(Exception.class, () -> service.preview(snapshot.file(), saved.fingerprint(), member.taskId(), 2));
	}
}
