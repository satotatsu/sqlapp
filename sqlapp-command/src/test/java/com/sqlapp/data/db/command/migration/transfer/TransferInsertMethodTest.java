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
import com.sqlapp.jdbc.bulk.BulkInsertMethod;

class TransferInsertMethodTest {
	@TempDir
	Path directory;

	private JDBCDataSource db() {
		var source = new JDBCDataSource();
		source.setUrl("jdbc:hsqldb:mem:insert_method_" + UUID.randomUUID());
		source.setUser("SA");
		source.setPassword("");
		return source;
	}

	private void sql(JDBCDataSource db, String... statements) throws Exception {
		try (var connection = db.getConnection(); var statement = connection.createStatement()) {
			for (var text : statements)
				statement.execute(text);
		}
	}

	private Path job(String method, boolean file) throws Exception {
		return Files.writeString(directory.resolve("transfer.yaml"),
				"formatVersion: 1\nsource: from\ntarget: to\ntables: [CHILD, PARENT]\nschemaMode: CREATE_MISSING\nchunkSize: 1\ninsertMethod: "
						+ method + (file ? "\ndataMode: FILE\nfiles: {directory: packages, format: CSV}\n" : "\n"));
	}

	@ParameterizedTest
	@CsvSource({ "AUTO,false", "NATIVE,false", "JDBC_BATCH,false", "AUTO,true", "NATIVE,true", "JDBC_BATCH,true" })
	void directAndFileJobsShareTheMethodAndPreserveRelationsNullEmptyAndDuplicateValues(String method, boolean file)
			throws Exception {
		var from = db();
		var to = db();
		try {
			sql(from, "CREATE TABLE PARENT(ID INTEGER NOT NULL PRIMARY KEY, TXT VARCHAR(20))",
					"CREATE TABLE CHILD(PARENT_ID INTEGER REFERENCES PARENT(ID), TXT VARCHAR(20))",
					"INSERT INTO PARENT VALUES(1,'one'),(2,NULL)", "INSERT INTO CHILD VALUES(1,''),(1,''),(2,NULL)");
			var path = job(method, file);
			if (file) {
				Files.writeString(directory.resolve("names.yaml"),
						"formatVersion: 1\ntables: {PARENT: {targetTable: PARENT_COPY}}\n");
				Files.writeString(path, Files.readString(path) + "mappingFile: names.yaml\n");
			}
			assertEquals(BulkInsertMethod.valueOf(method),
					new TransferJobConfigurationIO().read(path).configuration().effectiveInsertMethod());
			if (file) {
				var saved = new TransferFileExportService().export(path, Map.of("from", from));
				var captured = new TransferJobConfigurationIO().read(saved.file().getParent().resolve("transfer.yaml"));
				assertEquals(BulkInsertMethod.valueOf(method), captured.configuration().effectiveInsertMethod());
				var service = new TransferFileImportService();
				var result = service.execute(service.review(saved.file(), saved.fingerprint(), Map.of("to", to)),
						Map.of("to", to), directory);
				assertEquals(TransferJobExecutionService.Phase.COMPLETE, result.phase());
				assertTrue(result.verification().values().stream().allMatch(value -> value.isMatch()));
			} else {
				var service = new TransferJobExecutionService();
				var bindings = Map.of("from", from, "to", to);
				var result = service.execute(service.review(path, bindings, directory), bindings);
				assertEquals(TransferJobExecutionService.Phase.COMPLETE, result.phase());
			}
			try (var connection = to.getConnection();
					var statement = connection.createStatement();
					var rows = statement.executeQuery("SELECT COUNT(*) FROM CHILD WHERE TXT=''")) {
				assertTrue(rows.next());
				assertEquals(2, rows.getInt(1));
			}
			try (var connection = to.getConnection(); var statement = connection.createStatement()) {
				assertThrows(java.sql.SQLException.class,
						() -> statement.execute("INSERT INTO CHILD VALUES(999,'bad')"));
			}
		} finally {
			try {
				sql(from, "SHUTDOWN");
			} finally {
				sql(to, "SHUTDOWN");
			}
		}
	}

	@Test
	void changingTheMethodChangesThePlanAndCannotReuseAnEarlierReview() throws Exception {
		var from = db();
		var to = db();
		try {
			sql(from, "CREATE TABLE PARENT(ID INTEGER NOT NULL PRIMARY KEY)",
					"CREATE TABLE CHILD(ID INTEGER NOT NULL PRIMARY KEY)");
			var path = job("AUTO", false);
			var service = new TransferJobExecutionService();
			var bindings = Map.of("from", from, "to", to);
			var original = service.review(path, bindings, directory);
			job("JDBC_BATCH", false);
			var changed = service.review(path, bindings, directory);
			assertNotEquals(original.prepared().planFingerprint(), changed.prepared().planFingerprint());
			var failure = assertThrows(TransferJobExecutionService.ExecutionException.class,
					() -> service.execute(original, bindings));
			assertEquals(TransferJobExecutionService.Phase.REVALIDATION, failure.outcome().phase());
		} finally {
			try {
				sql(from, "SHUTDOWN");
			} finally {
				sql(to, "SHUTDOWN");
			}
		}
	}

	@Test
	void unavailableNativeProviderIsRejectedByReadOnlyPreflightWithAnActionableAlternative() throws Exception {
		var configuration = new TransferJobConfigurationIO().read(job("NATIVE", false)).configuration();
		var failure = assertThrows(TransferSchemaPreparation.ValidationException.class,
				() -> TransferJobPreparationService.validateInsertMethod(
						com.sqlapp.data.db.dialect.DefaultDialectHolder.DefaultDialect, configuration));
		assertTrue(failure.getMessage().contains("JDBC_BATCH"));
		assertTrue(failure.getMessage().contains("review again before execution"));
	}

	@Test
	void unknownMethodsAndSchemaOnlySelectionFailOffline() throws Exception {
		var io = new TransferJobConfigurationIO();
		var text = Files.readString(job("JDBC_BATCH", false));
		assertThrows(IllegalArgumentException.class, () -> io.validate(text.replace("JDBC_BATCH", "TYPO")));
		assertThrows(IllegalArgumentException.class, () -> io.validate(text + "dataMode: SCHEMA_ONLY\n"));
	}
}
