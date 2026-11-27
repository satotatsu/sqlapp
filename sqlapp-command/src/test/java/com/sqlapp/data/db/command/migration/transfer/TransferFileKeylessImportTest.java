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

class TransferFileKeylessImportTest {
	@TempDir
	Path directory;

	private JDBCDataSource database() {
		var db = new JDBCDataSource();
		db.setUrl("jdbc:hsqldb:mem:file_keyless_" + UUID.randomUUID());
		db.setUser("SA");
		db.setPassword("");
		return db;
	}

	private void sql(JDBCDataSource db, String... commands) throws Exception {
		try (var connection = db.getConnection(); var statement = connection.createStatement()) {
			for (var command : commands)
				statement.execute(command);
		}
	}

	private long count(JDBCDataSource db, String table) throws Exception {
		try (var connection = db.getConnection();
				var statement = connection.createStatement();
				var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
			rows.next();
			return rows.getLong(1);
		}
	}

	private TransferFilePackageIO.Snapshot export(JDBCDataSource source, String format, String tables, boolean create,
			boolean strict) throws Exception {
		var job = Files.writeString(directory.resolve("job.yaml"),
				"formatVersion: 1\nsource: from\ntarget: to\ntables: " + tables + "\nschemaMode: "
						+ (create ? "CREATE_MISSING" : "REQUIRE_EXISTING") + "\ncompatibilityPolicy: "
						+ (strict ? "ERROR" : "WARN")
						+ "\ndataMode: FILE\nchunkSize: 1\nfiles: {directory: packages, format: " + format + "}\n");
		return new TransferFileExportService().export(job, Map.of("from", source));
	}

	@ParameterizedTest
	@CsvSource({ "CSV, false", "CSV, true", "TSV, false", "TSV, true", "JSONL, false", "JSONL, true" })
	void importsKeylessAndTextKeyTablesWithDuplicatesNullsAndForeignKeys(String format, boolean create)
			throws Exception {
		var source = database();
		var target = database();
		try {
			sql(source, "CREATE TABLE PARENTS(ID INTEGER NOT NULL PRIMARY KEY)",
					"CREATE TABLE EVENTS(PARENT_ID INTEGER REFERENCES PARENTS(ID), NAME VARCHAR(40))",
					"CREATE TABLE TEXT_KEYS(ID VARCHAR(10) NOT NULL PRIMARY KEY, PARENT_ID INTEGER REFERENCES PARENTS(ID))",
					"CREATE TABLE EMPTY_EVENTS(NAME VARCHAR(20))", "INSERT INTO PARENTS VALUES(1)",
					"INSERT INTO EVENTS VALUES(1,'dup'),(1,'dup'),(NULL,NULL),(1,'')",
					"INSERT INTO TEXT_KEYS VALUES('b',1),('a',1)");
			if (!create)
				sql(target, "CREATE TABLE PARENTS(ID INTEGER NOT NULL PRIMARY KEY)",
						"CREATE TABLE EVENTS(PARENT_ID INTEGER REFERENCES PARENTS(ID), NAME VARCHAR(40))",
						"CREATE TABLE TEXT_KEYS(ID VARCHAR(10) NOT NULL PRIMARY KEY, PARENT_ID INTEGER REFERENCES PARENTS(ID))",
						"CREATE TABLE EMPTY_EVENTS(NAME VARCHAR(20))");
			var saved = export(source, format, "[EVENTS, TEXT_KEYS, PARENTS, EMPTY_EVENTS]", create, false);
			sql(source, "SHUTDOWN");
			var service = new TransferFileImportService();
			service.validatePackage(saved.file(), saved.fingerprint());
			var review = service.review(saved.file(), saved.fingerprint(), Map.of("to", target));
			assertTrue(review.schemaPreparation().warnings().stream()
					.anyMatch(warning -> warning.contains("[FILE_FULL_SCAN_INSERT]")));
			var result = service.execute(review, Map.of("to", target), directory);
			assertEquals(TransferJobExecutionService.Phase.COMPLETE, result.phase());
			assertEquals(4, count(target, "EVENTS"));
			assertEquals(2, count(target, "EVENTS WHERE NAME='dup'"));
			assertEquals(2, count(target, "TEXT_KEYS"));
			assertEquals(0, count(target, "EMPTY_EVENTS"));
			assertEquals(4, result.verification().size());
			assertTrue(result.verification().values().stream().allMatch(value -> value.isMatch()));
			assertTrue(result.verification().get("export-table-1").getChunks().getFirst().getExpectedHash()
					.startsWith("multiset-sha256-v1:"));
			assertThrows(Exception.class, () -> sql(target, "INSERT INTO EVENTS VALUES(999,'bad FK')"));
			assertThrows(Exception.class,
					() -> service.review(saved.file(), saved.fingerprint(), Map.of("to", target)));
			assertEquals(4, count(target, "EVENTS"));
		} finally {
			try {
				sql(source, "SHUTDOWN");
			} finally {
				sql(target, "SHUTDOWN");
			}
		}
	}

	@Test
	void strictPolicyRejectsFullScanBeforeDdlAndVerificationDetectsSameCountValueLoss() throws Exception {
		var source = database();
		var strictTarget = database();
		var roundedTarget = database();
		try {
			sql(source, "CREATE TABLE ITEMS(AMOUNT DECIMAL(10,2))", "INSERT INTO ITEMS VALUES(1.25),(1.25)");
			var service = new TransferFileImportService();
			var strict = export(source, "CSV", "[ITEMS]", true, true);
			assertThrows(Exception.class,
					() -> service.review(strict.file(), strict.fingerprint(), Map.of("to", strictTarget)));
			assertThrows(Exception.class, () -> count(strictTarget, "ITEMS"));
			sql(roundedTarget, "CREATE TABLE ITEMS(AMOUNT DECIMAL(10,1))");
			var saved = export(source, "CSV", "[ITEMS]", false, false);
			var review = service.review(saved.file(), saved.fingerprint(), Map.of("to", roundedTarget));
			var failure = assertThrows(TransferFileImportService.ImportException.class,
					() -> service.execute(review, Map.of("to", roundedTarget), directory));
			assertEquals(TransferJobExecutionService.Phase.VERIFICATION, failure.outcome().phase());
			assertEquals(2, count(roundedTarget, "ITEMS"));
			assertFalse(failure.outcome().verification().get("export-table-1").isMatch());
		} finally {
			try {
				sql(source, "SHUTDOWN");
			} finally {
				try {
					sql(strictTarget, "SHUTDOWN");
				} finally {
					sql(roundedTarget, "SHUTDOWN");
				}
			}
		}
	}
}
