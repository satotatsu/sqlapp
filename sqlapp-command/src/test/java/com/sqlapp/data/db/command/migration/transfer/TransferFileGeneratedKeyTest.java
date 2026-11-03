/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.Map;
import java.util.UUID;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TransferFileGeneratedKeyTest {
	@TempDir
	Path directory;

	private JDBCDataSource database() {
		var database = new JDBCDataSource();
		database.setUrl("jdbc:hsqldb:mem:file_generated_" + UUID.randomUUID());
		database.setUser("SA");
		database.setPassword("");
		return database;
	}

	private void sql(JDBCDataSource database, String... statements) throws Exception {
		try (var connection = database.getConnection(); var statement = connection.createStatement()) {
			for (var text : statements)
				statement.execute(text);
		}
	}

	private long count(JDBCDataSource database, String expression) throws Exception {
		try (var connection = database.getConnection();
				var statement = connection.createStatement();
				var rows = statement.executeQuery("SELECT COUNT(*) FROM " + expression)) {
			rows.next();
			return rows.getLong(1);
		}
	}

	private TransferFilePackageIO.Snapshot export(JDBCDataSource source, String format, String tables, String column,
			boolean strict) throws Exception {
		var configuration = "formatVersion: 1\nsource: from\ntarget: to\ntables: " + tables
				+ "\nschemaMode: CREATE_MISSING_WITH_IDENTITY\ndataMode: FILE\nchunkSize: 1\n" + "compatibilityPolicy: "
				+ (strict ? "ERROR" : "WARN") + "\n" + (column == null ? "" : "generatedKeyColumn: '" + column + "'\n")
				+ "files: {directory: packages, format: " + format + "}\n";
		var file = Files.writeString(directory.resolve("transfer.yaml"), configuration);
		return new TransferFileExportService().export(file, Map.of("from", source));
	}

	@ParameterizedTest
	@CsvSource({ "CSV, ''", "TSV, ''", "JSONL, ''", "CSV, 移行 ID", "TSV, 移行 ID", "JSONL, 移行 ID" })
	void sourceFreeImportCreatesTargetOnlyIdentityAndPreservesDuplicatesNullsAndForeignKeys(String format,
			String configuredName) throws Exception {
		var source = database();
		var target = database();
		try {
			sql(source, "CREATE TABLE PARENTS(ID INTEGER NOT NULL PRIMARY KEY)",
					"CREATE TABLE CHILDREN(PARENT_ID INTEGER REFERENCES PARENTS(ID), VALUE VARCHAR(40))",
					"INSERT INTO PARENTS VALUES(1)",
					"INSERT INTO CHILDREN VALUES(1,'duplicate'),(1,'duplicate'),(NULL,NULL)");
			var saved = export(source, format, "[CHILDREN, PARENTS]", configuredName.isEmpty() ? null : configuredName,
					false);
			String name = configuredName.isEmpty() ? "ID" : configuredName;
			assertTrue(saved.manifest().tables().stream().filter(table -> table.table().equals("CHILDREN"))
					.allMatch(table -> table.columns().equals(java.util.List.of("PARENT_ID", "VALUE"))));
			sql(source, "SHUTDOWN");
			var service = new TransferFileImportService();
			var review = service.review(saved.file(), saved.fingerprint(), Map.of("to", target));
			assertTrue(review.schemaPreparation().warnings().stream()
					.anyMatch(warning -> warning.startsWith(TransferKeylessChecks.GENERATED_TARGET_KEY)));
			var result = service.execute(review, Map.of("to", target), directory);
			assertEquals(TransferJobExecutionService.Phase.COMPLETE, result.phase());
			assertTrue(result.verification().values().stream().allMatch(value -> value.isMatch()));
			assertEquals(3, count(target, "CHILDREN"));
			assertEquals(2, count(target, "CHILDREN WHERE VALUE='duplicate'"));
			assertEquals(1, count(target, "CHILDREN WHERE VALUE IS NULL AND PARENT_ID IS NULL"));
			try (var connection = target.getConnection();
					var keys = connection.getMetaData().getPrimaryKeys(null, "PUBLIC", "CHILDREN")) {
				assertTrue(keys.next());
				assertEquals(name, keys.getString("COLUMN_NAME"));
				assertFalse(keys.next());
				try (var statement = connection.createStatement();
						var rows = statement.executeQuery("SELECT COUNT(DISTINCT \"" + name + "\") FROM CHILDREN")) {
					rows.next();
					assertEquals(3, rows.getLong(1));
				}
			}
			assertThrows(Exception.class,
					() -> sql(target, "INSERT INTO CHILDREN(PARENT_ID,VALUE) VALUES(999,'bad FK')"));
			assertThrows(Exception.class,
					() -> service.review(saved.file(), saved.fingerprint(), Map.of("to", target)));
		} finally {
			try {
				sql(source, "SHUTDOWN");
			} finally {
				sql(target, "SHUTDOWN");
			}
		}
	}

	@Test
	void generatedPrimaryKeysDoNotReplaceForeignKeysToOriginalUniqueColumns() throws Exception {
		var source = database();
		var target = database();
		try {
			sql(source, "CREATE TABLE PARENTS(CODE INTEGER NOT NULL UNIQUE)",
					"CREATE TABLE CHILDREN(CODE INTEGER REFERENCES PARENTS(CODE), VALUE VARCHAR(40))",
					"INSERT INTO PARENTS VALUES(42)", "INSERT INTO CHILDREN VALUES(42,'same'),(42,'same')");
			var saved = export(source, "CSV", "[CHILDREN, PARENTS]", null, false);
			var service = new TransferFileImportService();
			var review = service.review(saved.file(), saved.fingerprint(), Map.of("to", target));
			var result = service.execute(review, Map.of("to", target), directory);
			assertTrue(result.verification().values().stream().allMatch(value -> value.isMatch()));
			try (var connection = target.getConnection();
					var foreign = connection.getMetaData().getImportedKeys(null, "PUBLIC", "CHILDREN")) {
				assertTrue(foreign.next());
				assertEquals("CODE", foreign.getString("PKCOLUMN_NAME"));
				assertEquals("CODE", foreign.getString("FKCOLUMN_NAME"));
			}
			assertEquals(2, count(target, "CHILDREN WHERE CODE=42"));
			assertThrows(Exception.class, () -> sql(target, "INSERT INTO CHILDREN(CODE,VALUE) VALUES(99,'bad FK')"));
		} finally {
			try {
				sql(source, "SHUTDOWN");
			} finally {
				sql(target, "SHUTDOWN");
			}
		}
	}

	@Test
	void collisionsAndStrictPolicyStopBeforeDdlButDoNotBlockSourceOnlyExport() throws Exception {
		var source = database();
		var target = database();
		try {
			sql(source, "CREATE TABLE ITEMS(ID INTEGER)", "INSERT INTO ITEMS VALUES(1),(1)");
			var service = new TransferFileImportService();
			var collision = export(source, "CSV", "[ITEMS]", null, false);
			var failure = assertThrows(TransferSchemaPreparation.ValidationException.class,
					() -> service.review(collision.file(), collision.fingerprint(), Map.of("to", target)));
			assertTrue(failure.getMessage().contains("collides"));
			assertThrows(Exception.class, () -> count(target, "ITEMS"));
			var strict = export(source, "CSV", "[ITEMS]", "ROW_ID", true);
			assertThrows(TransferSchemaPreparation.ValidationException.class,
					() -> service.review(strict.file(), strict.fingerprint(), Map.of("to", target)));
			assertThrows(Exception.class, () -> count(target, "ITEMS"));
		} finally {
			try {
				sql(source, "SHUTDOWN");
			} finally {
				sql(target, "SHUTDOWN");
			}
		}
	}

	@Test
	void existingTargetsAreNotAlteredAndGeneratedColumnsDoNotHidePrecisionLoss() throws Exception {
		var source = database();
		var target = database();
		var rounded = database();
		try {
			sql(source, "CREATE TABLE ITEMS(VALUE DECIMAL(10,2))", "INSERT INTO ITEMS VALUES(1.25),(1.25)");
			sql(target, "CREATE TABLE ITEMS(VALUE DECIMAL(10,2))");
			var saved = export(source, "TSV", "[ITEMS]", null, false);
			var service = new TransferFileImportService();
			var review = service.review(saved.file(), saved.fingerprint(), Map.of("to", target));
			assertTrue(review.schemaPreparation().statements().isEmpty());
			assertFalse(review.schemaPreparation().warnings().stream()
					.anyMatch(warning -> warning.startsWith(TransferKeylessChecks.GENERATED_TARGET_KEY)));
			service.execute(review, Map.of("to", target), directory);
			try (var connection = target.getConnection();
					var columns = connection.getMetaData().getColumns(null, "PUBLIC", "ITEMS", "ID")) {
				assertFalse(columns.next());
			}
			sql(rounded,
					"CREATE TABLE ITEMS(ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, VALUE DECIMAL(10,1))");
			var roundedReview = service.review(saved.file(), saved.fingerprint(), Map.of("to", rounded));
			var failure = assertThrows(TransferFileImportService.ImportException.class,
					() -> service.execute(roundedReview, Map.of("to", rounded), directory));
			assertEquals(TransferJobExecutionService.Phase.VERIFICATION, failure.outcome().phase());
			assertEquals(2, count(rounded, "ITEMS"));
			assertFalse(failure.outcome().verification().values().stream().allMatch(value -> value.isMatch()));
		} finally {
			try {
				sql(source, "SHUTDOWN");
			} finally {
				try {
					sql(target, "SHUTDOWN");
				} finally {
					sql(rounded, "SHUTDOWN");
				}
			}
		}
	}
}
