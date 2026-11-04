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

class TransferFileNameMappingTest {
	@TempDir
	Path directory;

	private JDBCDataSource database() {
		var db = new JDBCDataSource();
		db.setUrl("jdbc:hsqldb:mem:file_mapping_" + UUID.randomUUID());
		db.setUser("SA");
		db.setPassword("");
		return db;
	}

	private void sql(JDBCDataSource db, String... statements) throws Exception {
		try (var connection = db.getConnection(); var statement = connection.createStatement()) {
			for (var text : statements)
				statement.execute(text);
		}
	}

	private long count(JDBCDataSource db, String expression) throws Exception {
		try (var connection = db.getConnection();
				var statement = connection.createStatement();
				var rows = statement.executeQuery("SELECT COUNT(*) FROM " + expression)) {
			rows.next();
			return rows.getLong(1);
		}
	}

	private Path job(String format, String mode, boolean strict, String tables) throws Exception {
		return Files.writeString(directory.resolve("job.yaml"),
				"formatVersion: 1\nsource: from\ntarget: to\ntables: " + tables + "\ndataMode: FILE\nschemaMode: "
						+ mode + "\nmappingFile: names.yaml\nchunkSize: 1\n" + "compatibilityPolicy: "
						+ (strict ? "ERROR" : "WARN") + "\n"
						+ (mode.equals("CREATE_MISSING_WITH_IDENTITY") ? "generatedKeyColumn: ROW_ID\n" : "")
						+ "files: {directory: packages, format: " + format + "}\n");
	}

	@ParameterizedTest
	@CsvSource({ "CSV, REQUIRE_EXISTING", "TSV, REQUIRE_EXISTING", "JSONL, REQUIRE_EXISTING", "CSV, CREATE_MISSING",
			"TSV, CREATE_MISSING", "JSONL, CREATE_MISSING", "CSV, CREATE_MISSING_WITH_IDENTITY",
			"TSV, CREATE_MISSING_WITH_IDENTITY", "JSONL, CREATE_MISSING_WITH_IDENTITY" })
	void relocatedPackagesRetainOriginalDataAndApplyOnePinnedMappingToDdlCopyAndVerification(String format, String mode)
			throws Exception {
		var source = database();
		var target = database();
		try {
			sql(source, "CREATE TABLE PARENT(ID INTEGER NOT NULL PRIMARY KEY, LABEL VARCHAR(40))",
					"CREATE TABLE CHILD(PARENT_ID INTEGER REFERENCES PARENT(ID), PAYLOAD VARCHAR(40))",
					"CREATE TABLE TEXT_KEYS(CODE VARCHAR(12) NOT NULL PRIMARY KEY, PARENT_ID INTEGER REFERENCES PARENT(ID))",
					"INSERT INTO PARENT VALUES(1,'parent')",
					"INSERT INTO CHILD VALUES(1,'dup'),(1,'dup'),(NULL,NULL),(1,'')",
					"INSERT INTO TEXT_KEYS VALUES('b',1),('a',1)");
			if (mode.equals("REQUIRE_EXISTING"))
				sql(target,
						"CREATE TABLE \"Parents Copy\"(\"Parent Key\" INTEGER NOT NULL PRIMARY KEY, \"Description\" VARCHAR(40))",
						"CREATE TABLE \"Children Copy\"(\"Parent Key\" INTEGER REFERENCES \"Parents Copy\"(\"Parent Key\"), \"Text Value\" VARCHAR(40))",
						"CREATE TABLE \"Text Keys\"(\"Code Key\" VARCHAR(12) NOT NULL PRIMARY KEY, \"Parent Key\" INTEGER REFERENCES \"Parents Copy\"(\"Parent Key\"))");
			var originalMapping = Files.writeString(directory.resolve("names.yaml"), """
					formatVersion: 1
					tables:
					  PARENT: {targetTable: 'Parents Copy', columns: {ID: 'Parent Key', LABEL: Description}}
					  CHILD: {targetTable: 'Children Copy', columns: {PARENT_ID: 'Parent Key', PAYLOAD: 'Text Value'}}
					  TEXT_KEYS: {targetTable: 'Text Keys', columns: {CODE: 'Code Key', PARENT_ID: 'Parent Key'}}
					""");
			var definition = job(format, mode, false, "[CHILD, TEXT_KEYS, PARENT]");
			var original = Files.readString(definition);
			var saved = new TransferFileExportService().export(definition, Map.of("from", source));
			assertEquals(original, Files.readString(definition));
			var captured = new TransferJobConfigurationIO().read(saved.file().getParent().resolve("transfer.yaml"));
			assertEquals(TransferNameMappingIO.CAPTURED_FILE, captured.configuration().mappingFile());
			assertTrue(captured.text().startsWith("# transfer-name-mapping: sha256:"));
			assertEquals(saved.manifest().jobFingerprint(), captured.fingerprint());
			assertEquals(Files.readString(originalMapping), Files.readString(captured.mappingFile()));
			Files.delete(originalMapping);
			sql(source, "SHUTDOWN");
			var moved = Files.move(saved.file().getParent(), directory.resolve("moved-package"));
			var manifest = moved.resolve("manifest.json");
			var service = new TransferFileImportService();
			var description = service.describePackage(manifest, saved.fingerprint());
			assertNotNull(description.mappingFingerprint());
			assertEquals(3, description.tables().size());
			var describedChild = description.tables().stream().filter(value -> value.sourceTable().equals("CHILD"))
					.findFirst().orElseThrow();
			assertEquals("Children Copy", describedChild.targetTable());
			assertEquals(4, describedChild.recordedRows());
			assertTrue(describedChild.fullScanInsert());
			assertEquals(mode.equals("CREATE_MISSING_WITH_IDENTITY"), describedChild.generatedKeyIfCreated());
			assertEquals(List.of("PARENT_ID", "PAYLOAD"), describedChild.columns().stream()
					.map(TransferFileImportService.PackageColumn::sourceName).toList());
			assertEquals(List.of("Parent Key", "Text Value"), describedChild.columns().stream()
					.map(TransferFileImportService.PackageColumn::targetName).toList());
			assertThrows(UnsupportedOperationException.class, () -> description.tables().clear());
			assertThrows(UnsupportedOperationException.class, () -> describedChild.columns().clear());
			var describedParent = description.tables().stream().filter(value -> value.sourceTable().equals("PARENT"))
					.findFirst().orElseThrow();
			assertFalse(describedParent.fullScanInsert());
			assertFalse(describedParent.generatedKeyIfCreated());
			assertTrue(describedParent.columns().getFirst().primaryKey());
			assertTrue(describedParent.columns().getFirst().notNull());

			var child = saved.manifest().tables().stream().filter(table -> table.table().equals("CHILD")).findFirst()
					.orElseThrow();
			assertEquals(List.of("PARENT_ID", "PAYLOAD"), child.columns());
			assertEquals(4, service.preview(manifest, saved.fingerprint(), child.taskId()).rows().size());
			service.validatePackage(manifest, saved.fingerprint());
			var review = service.review(manifest, saved.fingerprint(), Map.of("to", target));
			var result = service.execute(review, Map.of("to", target), directory);
			assertEquals(TransferJobExecutionService.Phase.COMPLETE, result.phase());
			assertTrue(result.verification().values().stream().allMatch(value -> value.isMatch()));
			assertEquals(4, count(target, "\"Children Copy\""));
			assertEquals(2, count(target, "\"Children Copy\" WHERE \"Text Value\"='dup'"));
			assertEquals(1, count(target, "\"Children Copy\" WHERE \"Text Value\"=''"));
			assertEquals(1, count(target, "\"Children Copy\" WHERE \"Text Value\" IS NULL"));
			assertEquals(2, count(target, "\"Text Keys\""));
			try (var connection = target.getConnection();
					var fk = connection.getMetaData().getImportedKeys(null, "PUBLIC", "Children Copy")) {
				assertTrue(fk.next());
				assertEquals("Parents Copy", fk.getString("PKTABLE_NAME"));
				assertEquals("Parent Key", fk.getString("PKCOLUMN_NAME"));
				assertEquals("Parent Key", fk.getString("FKCOLUMN_NAME"));
			}
			assertThrows(Exception.class, () -> sql(target,
					"INSERT INTO \"Children Copy\"(\"Parent Key\",\"Text Value\") VALUES(999,'bad FK')"));
			assertThrows(Exception.class, () -> service.review(manifest, saved.fingerprint(), Map.of("to", target)));
		} finally {
			try {
				sql(source, "SHUTDOWN");
			} finally {
				sql(target, "SHUTDOWN");
			}
		}
	}

	@Test
	void mappingCanFreeTheDefaultGeneratedKeyNameWithoutDiscardingSourceValues() throws Exception {
		var source = database();
		var target = database();
		try {
			sql(source, "CREATE TABLE ITEMS(ID INTEGER)", "INSERT INTO ITEMS VALUES(7),(7),(NULL)");
			Files.writeString(directory.resolve("names.yaml"),
					"formatVersion: 1\ntables: {ITEMS: {targetTable: COPIED, columns: {ID: ORIGINAL_ID}}}\n");
			var definition = job("JSONL", "CREATE_MISSING_WITH_IDENTITY", false, "[ITEMS]");
			Files.writeString(definition, Files.readString(definition).replace("generatedKeyColumn: ROW_ID\n", ""));
			var saved = new TransferFileExportService().export(definition, Map.of("from", source));
			var service = new TransferFileImportService();
			var review = service.review(saved.file(), saved.fingerprint(), Map.of("to", target));
			var result = service.execute(review, Map.of("to", target), directory);
			assertTrue(result.verification().values().stream().allMatch(value -> value.isMatch()));
			assertEquals(2, count(target, "COPIED WHERE ORIGINAL_ID=7"));
			assertEquals(1, count(target, "COPIED WHERE ORIGINAL_ID IS NULL"));
			try (var connection = target.getConnection();
					var pk = connection.getMetaData().getPrimaryKeys(null, "PUBLIC", "COPIED")) {
				assertTrue(pk.next());
				assertEquals("ID", pk.getString("COLUMN_NAME"));
			}
		} finally {
			try {
				sql(source, "SHUTDOWN");
			} finally {
				sql(target, "SHUTDOWN");
			}
		}
	}

	@Test
	void changedMappingsCannotAuthorizePreviewOrTargetWritesAndStrictReviewRejectsRenamingWarnings() throws Exception {
		var source = database();
		var target = database();
		try {
			sql(source, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)", "INSERT INTO ITEMS VALUES(1)");
			Files.writeString(directory.resolve("names.yaml"),
					"formatVersion: 1\ntables: {ITEMS: {targetTable: COPIED, columns: {ID: KEY_ID}}}\n");
			var exporter = new TransferFileExportService();
			var service = new TransferFileImportService();
			var strict = exporter.export(job("CSV", "CREATE_MISSING", true, "[ITEMS]"), Map.of("from", source));
			assertThrows(TransferSchemaPreparation.ValidationException.class,
					() -> service.review(strict.file(), strict.fingerprint(), Map.of("to", target)));
			assertThrows(Exception.class, () -> count(target, "COPIED"));
			var saved = exporter.export(job("CSV", "CREATE_MISSING", false, "[ITEMS]"), Map.of("from", source));
			var review = service.review(saved.file(), saved.fingerprint(), Map.of("to", target));
			Files.writeString(saved.file().getParent().resolve(TransferNameMappingIO.CAPTURED_FILE),
					"formatVersion: 1\ntables: {ITEMS: {targetTable: DIFFERENT}}\n");
			var never = (javax.sql.DataSource) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { javax.sql.DataSource.class }, (proxy, method, args) -> {
						throw new AssertionError("Changed mapping must fail before target access");
					});
			assertThrows(Exception.class, () -> service.preview(saved.file(), saved.fingerprint(), "export-table-1"));
			assertThrows(Exception.class, () -> service.describePackage(saved.file(), saved.fingerprint()));
			assertThrows(Exception.class, () -> service.review(saved.file(), saved.fingerprint(), Map.of("to", never)));
			assertThrows(Exception.class, () -> service.execute(review, Map.of("to", never), directory));
			assertThrows(Exception.class, () -> count(target, "COPIED"));
			assertThrows(Exception.class, () -> count(target, "DIFFERENT"));
		} finally {
			try {
				sql(source, "SHUTDOWN");
			} finally {
				sql(target, "SHUTDOWN");
			}
		}
	}

	@Test
	void ambiguousOrOutOfScopeMappingsCannotPublishAndPackagesNeverFollowExternalMappingPaths() throws Exception {
		var source = database();
		try {
			sql(source, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)",
					"CREATE TABLE OTHER(ID INTEGER NOT NULL PRIMARY KEY)");
			Files.writeString(directory.resolve("names.yaml"),
					"formatVersion: 1\ntables: {ITEMS: {columns: {ID: A, MISSING: B}}}\n");
			var definition = job("CSV", "CREATE_MISSING", false, "[ITEMS]");
			assertThrows(TransferSchemaPreparation.ValidationException.class,
					() -> new TransferFileExportService().export(definition, Map.of("from", source)));
			Files.writeString(directory.resolve("names.yaml"),
					"formatVersion: 1\ntables: {OTHER: {targetTable: COPIED}}\n");
			assertThrows(TransferSchemaPreparation.ValidationException.class,
					() -> new TransferFileExportService().export(definition, Map.of("from", source)));
			Files.writeString(directory.resolve("names.yaml"),
					"formatVersion: 1\ntables: {ITEMS: {targetTable: COPIED}, OTHER: {targetTable: COPIED}}\n");
			var collision = job("CSV", "CREATE_MISSING", false, "[ITEMS, OTHER]");
			assertThrows(TransferSchemaPreparation.ValidationException.class,
					() -> new TransferFileExportService().export(collision, Map.of("from", source)));
			try (var files = Files.walk(directory.resolve("packages"))) {
				assertFalse(files.anyMatch(path -> path.getFileName().toString().equals("manifest.json")));
			}
			var original = new TransferJobConfigurationIO().read(definition);
			assertThrows(IllegalArgumentException.class, () -> TransferFilePackageIO.mapping(original));
		} finally {
			sql(source, "SHUTDOWN");
		}
	}
}
