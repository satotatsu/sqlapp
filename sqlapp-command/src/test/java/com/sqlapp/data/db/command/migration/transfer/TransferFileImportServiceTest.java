/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.migration.internal.BoundedMigrationFile;

class TransferFileImportServiceTest {
	@TempDir
	Path directory;
	private final TransferFileImportService service = new TransferFileImportService();

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.EnumSource(TransferJobConfiguration.FileFormat.class)
	void roundTripsScalarValuesAndRelationshipsWithOnlyTargetBinding(TransferJobConfiguration.FileFormat format)
			throws Exception {
		roundTrip(format, "UTF-8", null);
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.CsvSource({ "CSV, UTF-8, VALIDATE", "CSV, windows-31j, SKIP",
			"CSV, UTF-16LE, NONE", "TSV, windows-31j, VALIDATE", "TSV, UTF-8, SKIP", "TSV, UTF-16LE, NONE" })
	void roundTripsConfiguredEncodingAndHeaders(TransferJobConfiguration.FileFormat format, String encoding,
			TransferJobConfiguration.HeaderMode headerMode) throws Exception {
		roundTrip(format, encoding, headerMode);
	}

	private void roundTrip(TransferJobConfiguration.FileFormat format, String encoding,
			TransferJobConfiguration.HeaderMode headerMode) throws Exception {
		var source = database();
		var target = database();
		try {
			roundTrip(source, target, format, encoding, headerMode);
		} finally {
			try {
				sql(source, "SHUTDOWN");
			} finally {
				sql(target, "SHUTDOWN");
			}
		}
	}

	private void roundTrip(JDBCDataSource source, JDBCDataSource target, TransferJobConfiguration.FileFormat format,
			String encoding, TransferJobConfiguration.HeaderMode headerMode) throws Exception {
		sql(source,
				"CREATE TABLE ITEMS(ID BIGINT NOT NULL PRIMARY KEY, NAME VARCHAR(100), AMOUNT DECIMAL(30,12), FLAG BOOLEAN, TS TIMESTAMP(9))",
				"CREATE TABLE ORDERS(ID INTEGER NOT NULL PRIMARY KEY, ITEM_ID BIGINT REFERENCES ITEMS(ID))",
				"CREATE TABLE EMPTY_ITEMS(ID INTEGER NOT NULL PRIMARY KEY)");
		String[] names = { null, "", " 日本語,\t\"文字\"\r\n\\N " };
		try (var connection = source.getConnection();
				var insert = connection.prepareStatement("INSERT INTO ITEMS VALUES(?,?,?,?,?)")) {
			for (int index = 0; index < names.length; index++) {
				insert.setLong(1, 9007199254740993L + index);
				insert.setString(2, names[index]);
				insert.setBigDecimal(3, new java.math.BigDecimal("123456789012345678.123456789012"));
				insert.setBoolean(4, index != 0);
				insert.setTimestamp(5, java.sql.Timestamp.valueOf("2024-02-29 23:59:59.123456789"));
				insert.executeUpdate();
			}
		}
		sql(source, "INSERT INTO ORDERS VALUES(1,9007199254740993)");
		var job = Files.writeString(directory.resolve("transfer.yaml"),
				"formatVersion: 1\nsource: from\ntarget: to\ntables: [ORDERS, ITEMS, EMPTY_ITEMS]\n"
						+ "schemaMode: CREATE_MISSING\ndataMode: FILE\nchunkSize: 1\nfiles: {directory: packages, format: "
						+ format + ", encoding: " + encoding + (headerMode == null ? "" : ", headerMode: " + headerMode)
						+ (format != TransferJobConfiguration.FileFormat.JSONL ? ", nullValue: __NULL__" : "") + "}\n");
		var exported = new TransferFileExportService().export(job, Map.of("from", source));
		assertEquals(format, exported.manifest().files().format());
		assertTrue(exported.manifest().tables().stream()
				.allMatch(table -> table.file().endsWith("." + format.name().toLowerCase(java.util.Locale.ROOT))));
		var review = service.review(exported.file(), exported.fingerprint(), Map.of("to", target));
		var outcome = service.execute(review, Map.of("to", target), directory);
		assertEquals(TransferJobExecutionService.Phase.COMPLETE, outcome.phase());
		assertEquals(3, count(target, "ITEMS"));
		assertEquals(0, count(target, "EMPTY_ITEMS"));
		assertEquals(3, outcome.verification().size());
		assertTrue(outcome.verification().values().stream().allMatch(result -> result.isMatch()));
		try (var connection = target.getConnection();
				var statement = connection.createStatement();
				var rows = statement.executeQuery("SELECT * FROM ITEMS ORDER BY ID")) {
			for (int index = 0; index < names.length; index++) {
				assertTrue(rows.next());
				assertEquals(9007199254740993L + index, rows.getLong("ID"));
				assertEquals(names[index], rows.getString("NAME"));
				assertEquals(new java.math.BigDecimal("123456789012345678.123456789012"), rows.getBigDecimal("AMOUNT"));
				assertEquals(index != 0, rows.getBoolean("FLAG"));
				assertEquals(123456789, rows.getTimestamp("TS").getNanos());
			}
		}
		assertThrows(Exception.class, () -> sql(target, "INSERT INTO ORDERS VALUES(2,999)"));
	}

	@Test
	void createsMissingTablesAndForeignKeysInReviewedOrderBeforeCopyWithoutSourceConnection() throws Exception {
		final var source = database();
		final var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY, NAME VARCHAR(40))",
				"CREATE TABLE ORDERS(ID INTEGER NOT NULL PRIMARY KEY, ITEM_ID INTEGER, CONSTRAINT FK_ORDER_ITEM FOREIGN KEY(ITEM_ID) REFERENCES ITEMS(ID))",
				"INSERT INTO ITEMS VALUES(1, 'one')", "INSERT INTO ORDERS VALUES(10,1)");
		sql(target, "CREATE SCHEMA ARCHIVE AUTHORIZATION DBA");
		final var exported = export(source, "[ORDERS, ITEMS]", "targetSchema: ARCHIVE\n", "CREATE_MISSING");
		final var bindings = Map.of("to", target);
		final var captured = service.reviewWithSqlDraft(exported.file(), exported.fingerprint(), bindings);
		final var review = captured.review();
		assertTrue(captured.schemaSqlDraft().contains("-- Job: " + exported.manifest().jobFingerprint()));
		assertTrue(captured.schemaSqlDraft().contains("-- Schema plan: " + review.schemaPreparation().fingerprint()));
		assertTrue(captured.schemaSqlDraft().contains(";\n"));
		assertEquals(exported, new TransferFilePackageIO().read(exported.file(), exported.fingerprint()));
		assertFalse(review.schemaPreparation().statements().isEmpty());
		assertEquals("export-table-2", review.schemaPreparation().statements().getFirst().taskId());
		assertTrue(review.schemaPreparation().statements().stream()
				.anyMatch(step -> step.sql().contains("REFERENCES ARCHIVE.ITEMS")));
		assertThrows(Exception.class, () -> count(target, "ARCHIVE.ITEMS"));
		final var result = service.execute(review, bindings, directory);
		assertEquals(TransferJobExecutionService.Phase.COMPLETE, result.phase());
		assertEquals(review.schemaPreparation().statements().size(), result.schemaPreparation().completedStatements());
		assertNull(result.schemaPreparation().attemptedStatement());
		assertEquals(1, count(target, "ARCHIVE.ITEMS"));
		assertEquals(1, count(target, "ARCHIVE.ORDERS"));
		assertThrows(Exception.class, () -> sql(target, "INSERT INTO ARCHIVE.ORDERS VALUES(20,999)"));
		assertTrue(Files.readString(result.auditFile()).contains("schemaPreparation"));
	}

	@Test
	void validatesExistingTablesBeforeDdlAndRejectsChangedMissingSet() throws Exception {
		final var source = database();
		final var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)",
				"CREATE TABLE OTHER(ID INTEGER NOT NULL PRIMARY KEY)");
		sql(target, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)", "INSERT INTO ITEMS VALUES(1)");
		final var exported = export(source, "[OTHER, ITEMS]", "", "CREATE_MISSING");
		final var bindings = Map.of("to", target);
		assertThrows(Exception.class, () -> service.review(exported.file(), exported.fingerprint(), bindings));
		assertThrows(Exception.class, () -> count(target, "OTHER"));
		sql(target, "DELETE FROM ITEMS");
		final var review = service.review(exported.file(), exported.fingerprint(), bindings);
		sql(target, "CREATE TABLE OTHER(ID INTEGER NOT NULL PRIMARY KEY)");
		final var failure = assertThrows(TransferFileImportService.ImportException.class,
				() -> service.execute(review, bindings, directory));
		assertEquals(TransferJobExecutionService.Phase.REVALIDATION, failure.outcome().phase());
		assertEquals(0, count(target, "OTHER"));
	}

	@Test
	void rejectsDefaultDependenciesAndMissingNamespaceWithoutAnyDdl() throws Exception {
		final var source = database();
		final var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY, VALUE INTEGER DEFAULT 3)");
		var exported = export(source, "[ITEMS]", "compatibilityPolicy: ERROR\n", "CREATE_MISSING");
		final var bindings = Map.of("to", target);
		final var first = exported;
		assertThrows(Exception.class, () -> service.review(first.file(), first.fingerprint(), bindings));
		assertThrows(Exception.class, () -> count(target, "ITEMS"));
		exported = export(source, "[ITEMS]", "targetSchema: MISSING\n", "CREATE_MISSING");
		final var second = exported;
		assertThrows(Exception.class, () -> service.review(second.file(), second.fingerprint(), bindings));
	}

	@Test
	void defaultCompatibilityWarningsAllowTryingDefaultsAndRetainTheEvidence() throws Exception {
		final var source = database();
		final var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY, VALUE INTEGER DEFAULT 3)",
				"INSERT INTO ITEMS(ID) VALUES(1)");
		final var exported = export(source, "[ITEMS]", "", "CREATE_MISSING");
		final var bindings = Map.of("to", target);
		final var review = service.review(exported.file(), exported.fingerprint(), bindings);
		assertFalse(review.schemaPreparation().warnings().isEmpty());
		final var result = service.execute(review, bindings, directory);
		assertEquals(TransferJobExecutionService.Phase.COMPLETE, result.phase());
		assertEquals(review.schemaPreparation().warnings(), result.schemaPreparation().warnings());
		assertTrue(Files.readString(result.auditFile()).contains("warnings"));
		sql(target, "INSERT INTO ITEMS(ID) VALUES(2)");
		try (var connection = target.getConnection();
				var query = connection.createStatement();
				var rows = query.executeQuery("SELECT VALUE FROM ITEMS WHERE ID=2")) {
			assertTrue(rows.next());
			assertEquals(3, rows.getInt(1));
		}
	}

	@Test
	void differentProductPackageIsAttemptedWithWarningsAndStrictModeCanRejectIt() throws Exception {
		for (boolean strict : new boolean[] { false, true }) {
			final var source = database();
			final var target = database();
			sql(source, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)", "INSERT INTO ITEMS VALUES(1)");
			final var exported = export(source, "[ITEMS]", strict ? "compatibilityPolicy: ERROR\n" : "",
					"CREATE_MISSING");
			final var schemaFile = exported.file().getParent().resolve("source-schema.xml");
			final var schema = (com.sqlapp.data.schemas.Schema) com.sqlapp.data.schemas.SchemaUtils
					.readXml(schemaFile.toFile());
			schema.setProductName("PostgreSQL");
			final var partitioning = new com.sqlapp.data.schemas.Partitioning(
					com.sqlapp.data.schemas.PartitioningType.Range);
			partitioning.getPartitioningColumns().add("ID");
			schema.getTables().get("ITEMS").setPartitioning(partitioning);
			schema.writeXml(schemaFile.toFile());
			Files.writeString(exported.file(),
					new com.sqlapp.util.JsonConverter()
							.toJsonString(new TransferFilePackageIO.Manifest(1, exported.manifest().jobFingerprint(),
									BoundedMigrationFile.sha256(schemaFile, null, "limit", "Schema"),
									exported.manifest().files(), exported.manifest().tables())));
			final var bindings = Map.of("to", target);
			if (strict)
				assertThrows(TransferSchemaPreparation.ValidationException.class,
						() -> service.review(exported.file(), null, bindings));
			else {
				final var review = service.review(exported.file(), null, bindings);
				assertTrue(review.schemaPreparation().warnings().size() >= 2);
				assertEquals(TransferJobExecutionService.Phase.COMPLETE,
						service.execute(review, bindings, directory).phase());
				assertEquals(1, count(target, "ITEMS"));
			}
		}
	}

	@Test
	void ddlFailureRetainsCompletedStatementsAndUncertainAttemptWithoutStartingCopy() throws Exception {
		final var source = database();
		final var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)",
				"CREATE TABLE OTHER(ID INTEGER NOT NULL PRIMARY KEY)", "INSERT INTO ITEMS VALUES(1)");
		final var exported = export(source, "[ITEMS, OTHER]", "", "CREATE_MISSING");
		final var review = service.review(exported.file(), exported.fingerprint(), Map.of("to", target));
		final var statements = new AtomicInteger();
		final var failing = new JDBCDataSource() {
			@Override
			public java.sql.Connection getConnection() throws java.sql.SQLException {
				return failSecondDdl(target.getConnection(), java.sql.Connection.class, statements);
			}
		};
		final var failure = assertThrows(TransferFileImportService.ImportException.class,
				() -> service.execute(review, Map.of("to", failing), directory));
		assertEquals(TransferJobExecutionService.Phase.SCHEMA_PREPARATION, failure.outcome().phase());
		assertEquals(1, failure.outcome().schemaPreparation().completedStatements());
		assertEquals(2, failure.outcome().schemaPreparation().attemptedStatement());
		assertEquals(2, failure.outcome().schemaPreparation().planned().size());
		assertNull(failure.outcome().copyResult());
		assertTrue(failure.outcome().committedProgress().isEmpty());
		assertEquals(0, count(target, "ITEMS"));
		assertThrows(Exception.class, () -> count(target, "OTHER"));
		assertTrue(Files.readString(failure.outcome().auditFile()).contains("SCHEMA_PREPARATION"));
		assertNull(failure.getCause());
	}

	@Test
	void reusesExistingEmptyParentAndCreatesOnlyItsMissingChild() throws Exception {
		final var source = database();
		final var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)",
				"CREATE TABLE ORDERS(ID INTEGER NOT NULL PRIMARY KEY, ITEM_ID INTEGER REFERENCES ITEMS(ID))",
				"INSERT INTO ITEMS VALUES(1)", "INSERT INTO ORDERS VALUES(10,1)");
		sql(target, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)");
		final var exported = export(source, "[ORDERS, ITEMS]", "", "CREATE_MISSING");
		final var bindings = Map.of("to", target);
		final var review = service.review(exported.file(), exported.fingerprint(), bindings);
		assertTrue(review.schemaPreparation().statements().stream()
				.allMatch(step -> step.taskId().equals("export-table-1")));
		assertEquals(TransferJobExecutionService.Phase.COMPLETE, service.execute(review, bindings, directory).phase());
		assertEquals(1, count(target, "ITEMS"));
		assertEquals(1, count(target, "ORDERS"));
	}

	@Test
	void preservesQuotedIdentifiersUniqueAndCheckConstraintsWhenCreatingTables() throws Exception {
		final var source = database();
		final var target = database();
		sql(source,
				"CREATE TABLE \"Line Items\"(\"Key Id\" INTEGER NOT NULL PRIMARY KEY CHECK(\"Key Id\" > 0), CODE VARCHAR(20) UNIQUE)",
				"INSERT INTO \"Line Items\" VALUES(1,'a')");
		final var exported = export(source, "['Line Items']", "", "CREATE_MISSING");
		final var bindings = Map.of("to", target);
		final var review = service.review(exported.file(), exported.fingerprint(), bindings);
		assertEquals(TransferJobExecutionService.Phase.COMPLETE, service.execute(review, bindings, directory).phase());
		assertEquals(1, count(target, "\"Line Items\""));
		assertThrows(Exception.class, () -> sql(target, "INSERT INTO \"Line Items\" VALUES(2,'a')"));
		assertThrows(Exception.class, () -> sql(target, "INSERT INTO \"Line Items\" VALUES(-1,'b')"));
	}

	@Test
	void roundTripsNullableEmptyQuotedUnicodeDecimalValuesWithOnlyTargetBindingAndPersistsAudit() throws Exception {
		final var source = database();
		final var target = database();
		final String ddl = "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY, NAME VARCHAR(100), AMOUNT DECIMAL(10,2), ENABLED BOOLEAN)";
		sql(source, ddl);
		sql(target, ddl);
		try (var connection = source.getConnection();
				var insert = connection.prepareStatement("INSERT INTO ITEMS VALUES(?,?,?,?)")) {
			final String[] values = { null, "", " 文, \"字\"\r\n line " };
			for (int index = 0; index < values.length; index++) {
				insert.setInt(1, index + 1);
				insert.setString(2, values[index]);
				insert.setBigDecimal(3, new java.math.BigDecimal("1.25"));
				insert.setBoolean(4, index % 2 == 0);
				insert.executeUpdate();
			}
		}
		final var exported = export(source, "[ITEMS]");
		final var bindings = Map.of("to", target);
		final var review = service.review(exported.file(), exported.fingerprint(), bindings);
		assertEquals(0, count(target, "ITEMS"));
		final var result = service.execute(review, bindings, directory);
		assertEquals(TransferJobExecutionService.Phase.COMPLETE, result.phase());
		assertEquals(3, result.copyResult().getProcessedRows());
		assertTrue(result.verificationRequested());
		assertTrue(result.verification().values().stream().allMatch(value -> value.isMatch()));
		assertTrue(Files.readString(result.auditFile()).contains("COMPLETE"));
		assertEquals(3, count(source, "ITEMS"));
		assertEquals(3, count(target, "ITEMS"));
		try (var connection = target.getConnection();
				var statement = connection.createStatement();
				var rows = statement.executeQuery("SELECT NAME FROM ITEMS ORDER BY ID")) {
			rows.next();
			assertNull(rows.getString(1));
			rows.next();
			assertEquals("", rows.getString(1));
			rows.next();
			assertEquals(" 文, \"字\"\r\n line ", rows.getString(1));
		}
		assertThrows(TransferFileImportService.ImportException.class,
				() -> service.execute(review, bindings, directory));
		assertEquals(3, count(target, "ITEMS"));
	}

	@Test
	void dependencyOrderAndExplicitTargetSchemaAreRetained() throws Exception {
		final var source = database();
		final var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)",
				"CREATE TABLE ORDERS(ID INTEGER NOT NULL PRIMARY KEY, ITEM_ID INTEGER REFERENCES ITEMS(ID))",
				"INSERT INTO ITEMS VALUES(1)", "INSERT INTO ORDERS VALUES(10,1)");
		sql(target, "CREATE SCHEMA ARCHIVE AUTHORIZATION DBA",
				"CREATE TABLE ARCHIVE.ITEMS(ID INTEGER NOT NULL PRIMARY KEY)",
				"CREATE TABLE ARCHIVE.ORDERS(ID INTEGER NOT NULL PRIMARY KEY, ITEM_ID INTEGER REFERENCES ARCHIVE.ITEMS(ID))");
		final var exported = export(source, "[ORDERS, ITEMS]", "targetSchema: ARCHIVE\n");
		final var bindings = Map.of("to", target);
		final var result = service.execute(service.review(exported.file(), exported.fingerprint(), bindings), bindings,
				directory);
		assertEquals(List.of("export-table-2", "export-table-1"),
				result.copyResult().getTasks().stream().map(task -> task.getTaskId()).toList());
		assertEquals(1, count(target, "ARCHIVE.ITEMS"));
		assertEquals(1, count(target, "ARCHIVE.ORDERS"));
	}

	@Test
	void recountsAndChecksHeadersKeyOrderAndTypesBeforeBorrowingTargetEvenWhenHashesHaveBeenUpdated() throws Exception {
		for (String content : new String[] { "\"BAD\"\n\"1\"\n\"2\"\n", "\"ID\"\n\"2\"\n\"1\"\n",
				"\"ID\"\n\"x\"\n\"2\"\n", "\"ID\"\n\"1\"\n" }) {
			final var source = database();
			sql(source, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)", "INSERT INTO ITEMS VALUES(1),(2)");
			final var exported = export(source, "[ITEMS]");
			final Path csv = exported.file().getParent().resolve("table-1.csv");
			Files.writeString(csv, content);
			final var previous = exported.manifest().tables().getFirst();
			final var changed = new TransferFilePackageIO.TableFile(previous.taskId(), previous.catalog(),
					previous.schema(), previous.table(), previous.columns(), previous.file(), previous.rows(),
					Files.size(csv), BoundedMigrationFile.sha256(csv, null, "limit", "CSV"));
			Files.writeString(exported.file(),
					new com.sqlapp.util.JsonConverter().toJsonString(new TransferFilePackageIO.Manifest(1,
							exported.manifest().jobFingerprint(), exported.manifest().schemaFingerprint(),
							exported.manifest().files(), List.of(changed))));
			final var borrowed = new AtomicInteger();
			final var neverOpen = new JDBCDataSource() {
				@Override
				public java.sql.Connection getConnection() throws java.sql.SQLException {
					borrowed.incrementAndGet();
					throw new java.sql.SQLException("must not borrow");
				}
			};
			assertThrows(Exception.class, () -> service.review(exported.file(), null, Map.of("to", neverOpen)));
			assertEquals(0, borrowed.get());
		}
	}

	@Test
	void changedPackageOrTargetBindingRejectsBeforeCopy() throws Exception {
		final var source = database();
		final var target = database();
		final var other = database();
		for (var database : List.of(source, target, other))
			sql(database, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)");
		sql(source, "INSERT INTO ITEMS VALUES(1)");
		final var exported = export(source, "[ITEMS]");
		final var review = service.review(exported.file(), exported.fingerprint(), Map.of("to", target));
		assertThrows(TransferFileImportService.ImportException.class,
				() -> service.execute(review, Map.of("to", other), directory));
		assertEquals(0, count(other, "ITEMS"));
		Files.writeString(exported.file().getParent().resolve("table-1.csv"), "changed");
		assertThrows(TransferFileImportService.ImportException.class,
				() -> service.execute(review, Map.of("to", target), directory));
		assertEquals(0, count(target, "ITEMS"));
	}

	@Test
	void copyAndVerificationFailuresRetainCommittedWorkAndFailureAudit() throws Exception {
		final var source = database();
		final var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)", "INSERT INTO ITEMS VALUES(1),(2)");
		sql(target, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY CHECK(ID < 2))");
		var exported = export(source, "[ITEMS]");
		var bindings = Map.of("to", target);
		final var review = service.review(exported.file(), exported.fingerprint(), bindings);
		final var failure = assertThrows(TransferFileImportService.ImportException.class,
				() -> service.execute(review, Map.of("to", target), directory));
		assertEquals(TransferJobExecutionService.Phase.COPY, failure.outcome().phase());
		assertEquals(TransferFailureDiagnostic.Kind.CHECK_CONSTRAINT, failure.outcome().failureDiagnostic().kind());
		assertEquals("23513", failure.outcome().failureDiagnostic().sqlState());
		assertTrue(Files.readString(failure.outcome().auditFile()).contains("failureDiagnostic"));
		assertEquals(1, failure.outcome().committedProgress().get("export-table-1").getProcessedRowsAfter());
		assertEquals("export-table-1", failure.outcome().stoppedTaskId());
		assertTrue(Files.isRegularFile(failure.outcome().auditFile()));
		assertEquals(1, count(target, "ITEMS"));
		final var decimalSource = database();
		final var roundedTarget = database();
		sql(decimalSource, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY, AMOUNT DECIMAL(10,2))",
				"INSERT INTO ITEMS VALUES(1,1.25)");
		sql(roundedTarget, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY, AMOUNT DECIMAL(10,0))");
		exported = export(decimalSource, "[ITEMS]");
		bindings = Map.of("to", roundedTarget);
		final var roundedReview = service.review(exported.file(), exported.fingerprint(), bindings);
		final var mismatch = assertThrows(TransferFileImportService.ImportException.class,
				() -> service.execute(roundedReview, Map.of("to", roundedTarget), directory));
		assertEquals(TransferJobExecutionService.Phase.VERIFICATION, mismatch.outcome().phase());
		assertEquals(TransferFailureDiagnostic.Kind.VERIFICATION_MISMATCH,
				mismatch.outcome().failureDiagnostic().kind());
		assertEquals(1, mismatch.outcome().copyResult().getProcessedRows());
		assertFalse(mismatch.outcome().verification().get("export-table-1").isMatch());
		assertEquals(1, count(roundedTarget, "ITEMS"));
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.CsvSource({ "CSV,false", "TSV,false", "JSONL,false", "CSV,true" })
	void stagedFileImportReplacesRelatedRowsOrRollsBackEveryTable(TransferJobConfiguration.FileFormat format,
			boolean reject) throws Exception {
		var source = database();
		var target = database();
		try {
			sql(source, "CREATE TABLE PARENT(ID INTEGER PRIMARY KEY)",
					"CREATE TABLE CHILD(PARENT_ID INTEGER REFERENCES PARENT(ID), TXT VARCHAR(20))",
					"INSERT INTO PARENT VALUES(1)", "INSERT INTO CHILD VALUES(1,'new'),(1,'new')");
			sql(target, "CREATE TABLE PARENT(ID INTEGER PRIMARY KEY)",
					"CREATE TABLE CHILD(PARENT_ID INTEGER REFERENCES PARENT(ID), TXT VARCHAR(20)"
							+ (reject ? " CHECK(TXT <> 'new')" : "") + ")",
					"INSERT INTO PARENT VALUES(99)", "INSERT INTO CHILD VALUES(99,'old')");
			var exported = export(source, "[CHILD, PARENT]", "targetPreparation: STAGED_REPLACE\n", "REQUIRE_EXISTING",
					format);
			var service = new TransferFileImportService();
			var bindings = Map.of("to", target);
			var reviewed = service.reviewWithSqlDraft(exported.file(), exported.fingerprint(), bindings);
			assertNotNull(reviewed.review().staging());
			assertTrue(reviewed.schemaSqlDraft().contains("-- DELETE"));
			assertEquals(1, count(target, "CHILD"));
			if (reject) {
				var failure = assertThrows(TransferFileImportService.ImportException.class,
						() -> service.execute(reviewed.review(), bindings, directory));
				assertEquals(TransferStagedReplacement.State.ROLLED_BACK, failure.outcome().staging().state());
				assertEquals(1, count(target, "CHILD WHERE PARENT_ID=99"));
				assertEquals(1, count(target, "PARENT WHERE ID=99"));
				assertTrue(failure.outcome().staging().committedRows().isEmpty());
			} else {
				var outcome = service.execute(reviewed.review(), bindings, directory);
				assertEquals(TransferStagedReplacement.State.COMMITTED, outcome.staging().state());
				assertEquals(2, count(target, "CHILD WHERE PARENT_ID=1"));
				assertEquals(0, count(target, "PARENT WHERE ID=99"));
				assertEquals(2, outcome.staging().cleanedStages().size());
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
	void stagedFileImportCreatesMappedMissingTargetAfterStaging() throws Exception {
		var source = database();
		var target = database();
		try {
			sql(source, "CREATE TABLE ITEMS(ID INTEGER PRIMARY KEY)", "INSERT INTO ITEMS VALUES(17)");
			Files.writeString(directory.resolve("names.yaml"),
					"formatVersion: 1\ntables: {ITEMS: {targetTable: MAPPED_ITEMS, columns: {ID: ITEM_ID}}}\n");
			var exported = export(source, "[ITEMS]", "targetPreparation: STAGED_REPLACE\nmappingFile: names.yaml\n",
					"CREATE_MISSING");
			var service = new TransferFileImportService();
			var bindings = Map.of("to", target);
			var review = service.review(exported.file(), exported.fingerprint(), bindings);
			var outcome = service.execute(review, bindings, directory);
			assertEquals(TransferStagedReplacement.State.COMMITTED, outcome.staging().state());
			assertEquals(1, count(target, "MAPPED_ITEMS WHERE ITEM_ID=17"));
		} finally {
			try {
				sql(source, "SHUTDOWN");
			} finally {
				sql(target, "SHUTDOWN");
			}
		}
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(booleans = { false, true })
	void comparesSavedFileImportAfterUnconfirmedCommit(boolean committed) throws Exception {
		var source = database();
		var target = database();
		try {
			sql(source, "CREATE TABLE ITEMS(ID INTEGER PRIMARY KEY)", "INSERT INTO ITEMS VALUES(17)");
			sql(target, "CREATE TABLE ITEMS(ID INTEGER PRIMARY KEY)", "INSERT INTO ITEMS VALUES(99)");
			var exported = export(source, "[ITEMS]", "targetPreparation: STAGED_REPLACE\n");
			var service = new TransferFileImportService();
			var review = service.review(exported.file(), exported.fingerprint(), Map.of("to", target));
			var failure = assertThrows(TransferFileImportService.ImportException.class, () -> service.execute(review,
					Map.of("to", TransferStagedReplacementTest.lostCommitReply(target, committed)), directory));
			assertEquals(TransferStagedReplacement.State.COMMIT_UNCERTAIN, failure.outcome().staging().state());
			var report = failure.outcome().auditFile();
			var saved = Files.readString(report);
			var comparison = TransferStagedReplacement.compareRetainedStageReport(report, target);
			assertEquals(committed, comparison.comparison().tables().getFirst().matchesTarget());
			assertTrue(comparison.comparison().tables().getFirst().matchesRecordedStaging());
			assertEquals(saved, Files.readString(report));
			assertThrows(IllegalArgumentException.class,
					() -> TransferStagedReplacement.compareRetainedStageReport(report, source));
			var command = new CompareTransferStagingCommand();
			command.setResultFile(report.toFile());
			command.setWorkspaceDirectory(directory.toFile());
			command.setDataSource(target);
			command.setCloseDataSource(false);
			command.run();
			assertEquals(comparison, new TransferStagingComparisonReportIO().read(command.getComparisonReport()));
		} finally {
			try {
				sql(source, "SHUTDOWN");
			} finally {
				sql(target, "SHUTDOWN");
			}
		}
	}

	private TransferFilePackageIO.Snapshot export(JDBCDataSource source, String tables) throws Exception {
		return export(source, tables, "");
	}

	private TransferFilePackageIO.Snapshot export(JDBCDataSource source, String tables, String options)
			throws Exception {
		return export(source, tables, options, "REQUIRE_EXISTING");
	}

	private TransferFilePackageIO.Snapshot export(JDBCDataSource source, String tables, String options,
			String schemaMode) throws Exception {
		return export(source, tables, options, schemaMode, TransferJobConfiguration.FileFormat.CSV);
	}

	private TransferFilePackageIO.Snapshot export(JDBCDataSource source, String tables, String options,
			String schemaMode, TransferJobConfiguration.FileFormat format) throws Exception {
		final Path job = Files.writeString(directory.resolve("transfer.yaml"),
				"formatVersion: 1\nsource: from\ntarget: to\n" + "schemaMode: " + schemaMode
						+ "\ndataMode: FILE\nchunkSize: 1\ntables: " + tables + "\n" + options
						+ "files: {directory: packages, format: " + format
						+ (format == TransferJobConfiguration.FileFormat.JSONL ? "" : ", nullValue: '\\N'") + "}\n");
		return new TransferFileExportService().export(job, Map.of("from", source));
	}

	private static JDBCDataSource database() {
		final var dataSource = new JDBCDataSource();
		dataSource.setUrl("jdbc:hsqldb:mem:csv_import_" + UUID.randomUUID());
		dataSource.setUser("SA");
		dataSource.setPassword("");
		return dataSource;
	}

	private static <T> T failSecondDdl(T delegate, Class<T> type, AtomicInteger statements) {
		return type.cast(java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
				(proxy, method, args) -> {
					try {
						if (method.getName().equals("execute") && args != null && args[0] instanceof String sql
								&& sql.startsWith("CREATE") && statements.incrementAndGet() == 2)
							throw new java.sql.SQLException("Embedded DDL failure", "42000");
						final Object value = method.invoke(delegate, args);
						if (value instanceof java.sql.Statement statement)
							return failSecondDdl(statement, java.sql.Statement.class, statements);
						return value;
					} catch (java.lang.reflect.InvocationTargetException failure) {
						throw failure.getCause();
					}
				}));
	}

	private static void sql(JDBCDataSource source, String... statements) throws Exception {
		try (var connection = source.getConnection(); var statement = connection.createStatement()) {
			for (String text : statements)
				statement.execute(text);
		}
	}

	private static int count(JDBCDataSource source, String table) throws Exception {
		try (var connection = source.getConnection();
				var statement = connection.createStatement();
				var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
			rows.next();
			return rows.getInt(1);
		}
	}
}
