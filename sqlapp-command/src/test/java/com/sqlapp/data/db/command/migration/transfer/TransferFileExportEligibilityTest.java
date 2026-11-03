/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TransferFileExportEligibilityTest {
	@TempDir
	Path directory;

	@ParameterizedTest
	@EnumSource(TransferJobConfiguration.FileFormat.class)
	void exportsAndPreviewsKeylessDuplicatesNullsAndTextKeysWithoutTargetBinding(
			TransferJobConfiguration.FileFormat format) throws Exception {
		var source = new JDBCDataSource();
		source.setUrl("jdbc:hsqldb:mem:export_keys_" + UUID.randomUUID());
		source.setUser("SA");
		source.setPassword("");
		try {
			try (var connection = source.getConnection(); var sql = connection.createStatement()) {
				sql.execute("CREATE TABLE LOGS(ID INTEGER, NAME VARCHAR(100))");
				sql.execute("INSERT INTO LOGS VALUES(1,'same'),(1,'same'),(NULL,NULL),(NULL,'')");
				sql.execute("CREATE TABLE TEXT_KEYS(ID VARCHAR(10) NOT NULL PRIMARY KEY, NAME VARCHAR(30))");
				sql.execute("INSERT INTO TEXT_KEYS VALUES('key','value')");
			}
			var job = Files.writeString(directory.resolve("export.yaml"),
					"formatVersion: 1\nsource: from\ntarget: later\ntables: [LOGS, TEXT_KEYS]\nschemaMode: REQUIRE_EXISTING\ncompatibilityPolicy: ERROR\ndataMode: FILE\nfiles: {directory: packages, format: "
							+ format
							+ (format == TransferJobConfiguration.FileFormat.JSONL ? "" : ", nullValue: __NULL__")
							+ "}\n");
			var exported = new TransferFileExportService().export(job, Map.of("from", source));
			assertEquals(exported, new TransferFilePackageIO().read(exported.file(), exported.fingerprint()));
			assertEquals(4, exported.manifest().tables().stream().filter(table -> table.table().equals("LOGS"))
					.findFirst().orElseThrow().rows());
			assertEquals(2, exported.manifest().warnings().stream()
					.filter(warning -> warning.message().contains("[FILE_FULL_SCAN_INSERT]")).count());
			var service = new TransferFileImportService();
			var preview = service.preview(exported.file(), exported.fingerprint(), "export-table-1");
			assertEquals(4, preview.rows().size());
			assertEquals(2, preview.rows().stream()
					.filter(row -> row.get(0).text().equals("1") && row.get(1).text().equals("\"same\"")).count());
			assertEquals(1,
					preview.rows().stream().filter(row -> row.get(0).sqlNull() && row.get(1).sqlNull()).count());
			assertEquals(1, preview.rows().stream()
					.filter(row -> row.get(0).sqlNull() && row.get(1).text().equals("\"\"")).count());
			assertEquals(1, service.preview(exported.file(), exported.fingerprint(), "export-table-2").rows().size());
			service.validatePackage(exported.file(), exported.fingerprint());
		} finally {
			try (var connection = source.getConnection(); var sql = connection.createStatement()) {
				sql.execute("SHUTDOWN");
			}
		}
	}
}
