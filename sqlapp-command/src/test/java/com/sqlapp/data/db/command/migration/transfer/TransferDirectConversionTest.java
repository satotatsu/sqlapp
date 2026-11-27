/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Value conversions in direct JDBC copies, verified against embedded databases.
 */
class TransferDirectConversionTest {
	@TempDir
	Path directory;

	private static final String MAPPING = """
			formatVersion: 1
			tables:
			  CUSTOMERS:
			    targetTable: "顧客"
			    columns:
			      ID: {type: BIGINT}
			      NAME: {name: "氏名", conversion: "value == null ? null : value.trim().toUpperCase()"}
			      STATUS: {type: "VARCHAR(10)", conversion: "value == 'A' ? 'ACTIVE' : 'INACTIVE'"}
			      NOTE: {conversion: "row['NAME'] + '/' + column"}
			  EVENTS:
			    columns: {TXT: {conversion: "value == null ? 'none' : value + '!'"}}
			""";

	@Test
	void copiesConvertedValuesAndVerifiesThemForKeysetAndFullScanTables() throws Exception {
		var source = database();
		var target = database();
		initialize(source);
		var service = new TransferJobExecutionService();
		var bindings = Map.of("from", source, "to", target);
		var review = service.review(job(MAPPING, "CREATE_MISSING", ""), bindings, directory);
		var dataJob = Files.readString(review.prepared().dataJob().file());
		assertTrue(dataJob.contains("columnConversions"), dataJob);
		var outcome = service.execute(review, bindings);
		assertEquals(TransferJobExecutionService.Phase.COMPLETE, outcome.phase());
		assertTrue(outcome.verificationResult().isMatch());
		assertEquals(List.of("1|ALICE|ACTIVE| alice /NOTE", "2|BOB|INACTIVE|bob/NOTE", "3|null|INACTIVE|null/NOTE"),
				rows(target, "SELECT ID,\"氏名\",STATUS,NOTE FROM PUBLIC.\"顧客\" ORDER BY ID"));
		// Mapped types define the target table, including a key column's type.
		assertEquals(List.of("ID|BIGINT|64", "STATUS|VARCHAR|10"), rows(target,
				"SELECT COLUMN_NAME,TYPE_NAME,COLUMN_SIZE FROM INFORMATION_SCHEMA.SYSTEM_COLUMNS WHERE TABLE_NAME='顧客' AND COLUMN_NAME IN ('ID','STATUS') ORDER BY COLUMN_NAME"));
		assertEquals(List.of("!", "a!", "none"), rows(target, "SELECT TXT FROM PUBLIC.EVENTS ORDER BY TXT"));
		// Source rows are read, never rewritten.
		assertEquals(List.of(" alice ", "bob", "null"), rows(source, "SELECT NAME FROM CUSTOMERS ORDER BY ID"));
	}

	@Test
	void verificationDetectsTargetRowsThatDifferFromConvertedSourceValues() throws Exception {
		var source = database();
		var target = database();
		initialize(source);
		var service = new TransferJobExecutionService();
		var bindings = Map.of("from", source, "to", target);
		// The existing target table holds the unconverted value under a trigger that
		// keeps it, so the converted source no longer matches.
		sql(target,
				"CREATE TABLE \"顧客\"(ID BIGINT NOT NULL PRIMARY KEY,\"氏名\" VARCHAR(40),STATUS VARCHAR(10),NOTE VARCHAR(80))",
				"CREATE TABLE EVENTS(TXT VARCHAR(20))",
				"CREATE TRIGGER KEEP_STATUS BEFORE INSERT ON \"顧客\" REFERENCING NEW ROW AS N FOR EACH ROW SET N.STATUS = 'X'");
		var review = service.review(job(MAPPING, "REQUIRE_EXISTING", ""), bindings, directory);
		var failure = assertThrows(TransferJobExecutionService.ExecutionException.class,
				() -> service.execute(review, bindings));
		assertEquals(TransferJobExecutionService.Phase.VERIFICATION, failure.outcome().phase());
		assertFalse(failure.outcome().verificationResult().isMatch());
	}

	@ParameterizedTest
	@ValueSource(strings = { "ID: {conversion: \"value + 1\"}", "NAME: {conversion: \"value.(\"}",
			"MISSING: {conversion: value}" })
	void rejectsKeyConversionsInvalidExpressionsAndUnknownColumnsBeforeCopying(String conversion) throws Exception {
		var source = database();
		var target = database();
		initialize(source);
		var mapping = """
				formatVersion: 1
				tables:
				  CUSTOMERS:
				    columns: {%s}
				""".formatted(conversion);
		var service = new TransferJobExecutionService();
		var bindings = Map.of("from", source, "to", target);
		var failure = assertThrows(IllegalArgumentException.class,
				() -> service.review(job(mapping, "CREATE_MISSING", ""), bindings, directory));
		var message = failure.getMessage();
		if (conversion.startsWith("ID"))
			assertTrue(message.contains("primary key column CUSTOMERS.ID"), message);
		else if (conversion.startsWith("NAME"))
			assertTrue(message.contains("Invalid column conversion expression for CUSTOMERS.NAME"), message);
		else
			assertTrue(message.contains("CUSTOMERS.MISSING"), message);
		try (var c = target.getConnection(); var r = c.getMetaData().getTables(null, "PUBLIC", "CUSTOMERS", null)) {
			assertFalse(r.next());
		}
	}

	@Test
	void conversionFailureStopsTheCopyWithoutExposingTheSourceValue() throws Exception {
		var source = database();
		var target = database();
		initialize(source);
		var mapping = """
				formatVersion: 1
				tables:
				  CUSTOMERS:
				    columns: {NAME: {conversion: "value.substring(100)"}}
				""";
		var service = new TransferJobExecutionService();
		var bindings = Map.of("from", source, "to", target);
		var review = service.review(job(mapping, "CREATE_MISSING", ""), bindings, directory);
		var failure = assertThrows(TransferJobExecutionService.ExecutionException.class,
				() -> service.execute(review, bindings));
		assertEquals(TransferJobExecutionService.Phase.COPY, failure.outcome().phase());
		assertFalse(failure.getMessage().contains("alice"), failure.getMessage());
	}

	@Test
	void stagedReplacementCarriesConversionsIntoTheStagingCopy() throws Exception {
		var source = database();
		var target = database();
		initialize(source);
		sql(target,
				"CREATE TABLE \"顧客\"(ID BIGINT NOT NULL PRIMARY KEY,\"氏名\" VARCHAR(40),STATUS VARCHAR(10),NOTE VARCHAR(80))",
				"CREATE TABLE EVENTS(TXT VARCHAR(20))", "INSERT INTO \"顧客\" VALUES(99,'old','0',NULL)",
				"INSERT INTO EVENTS VALUES('old')");
		var service = new TransferJobExecutionService();
		var bindings = Map.of("from", source, "to", target);
		var review = service.review(job(MAPPING, "CREATE_MISSING", "targetPreparation: STAGED_REPLACE\n"), bindings,
				directory);
		var outcome = service.execute(review, bindings);
		assertEquals(TransferStagedReplacement.State.COMMITTED, outcome.staging().state());
		assertTrue(outcome.verificationResult().isMatch());
		assertEquals(List.of("1|ALICE|ACTIVE", "2|BOB|INACTIVE", "3|null|INACTIVE"),
				rows(target, "SELECT ID,\"氏名\",STATUS FROM PUBLIC.\"顧客\" ORDER BY ID"));
		assertEquals(List.of("!", "a!", "none"), rows(target, "SELECT TXT FROM PUBLIC.EVENTS ORDER BY TXT"));
	}

	@Test
	void typeChangesThatCannotHoldTheSourceValueStopTheCopy() throws Exception {
		var source = database();
		var target = database();
		initialize(source);
		var mapping = """
				formatVersion: 1
				tables:
				  CUSTOMERS:
				    columns: {NAME: {type: INT}}
				""";
		var service = new TransferJobExecutionService();
		var bindings = Map.of("from", source, "to", target);
		var review = service.review(job(mapping, "CREATE_MISSING", ""), bindings, directory);
		var failure = assertThrows(TransferJobExecutionService.ExecutionException.class,
				() -> service.execute(review, bindings));
		assertEquals(TransferJobExecutionService.Phase.COPY, failure.outcome().phase());
	}

	@Test
	void schemaOnlyAppliesTypesAndFileTransportsRejectTypesAndConversions() throws Exception {
		var source = database();
		var target = database();
		initialize(source);
		var bindings = Map.of("from", source, "to", target);
		var service = new TransferJobExecutionService();
		var schemaOnly = service.review(job(MAPPING, "CREATE_MISSING", "dataMode: SCHEMA_ONLY\n"), bindings, directory);
		service.execute(schemaOnly, bindings);
		assertEquals(List.of("STATUS|VARCHAR|10"), rows(target,
				"SELECT COLUMN_NAME,TYPE_NAME,COLUMN_SIZE FROM INFORMATION_SCHEMA.SYSTEM_COLUMNS WHERE TABLE_NAME='顧客' AND COLUMN_NAME='STATUS'"));
		assertEquals(List.of("0"), rows(target, "SELECT COUNT(*) FROM PUBLIC.\"顧客\""));
		IllegalArgumentException failure;
		Files.writeString(directory.resolve("transfer.yaml"),
				"formatVersion: 1\nsource: from\ntarget: to\ntables: [CUSTOMERS]\nschemaMode: CREATE_MISSING\nmappingFile: mapping.yaml\ndataMode: FILE\nfiles: {directory: files, format: CSV}\n");
		failure = assertThrows(IllegalArgumentException.class,
				() -> new TransferFileExportService().inspectConfiguration(directory.resolve("transfer.yaml")));
		assertTrue(failure.getMessage().contains("FILE export"), failure.getMessage());
	}

	private Path job(String mapping, String schemaMode, String extra) throws Exception {
		Files.writeString(directory.resolve("mapping.yaml"), mapping);
		return Files.writeString(directory.resolve("transfer.yaml"),
				"formatVersion: 1\nsource: from\ntarget: to\ntables: [CUSTOMERS, EVENTS]\nschemaMode: " + schemaMode
						+ "\nmappingFile: mapping.yaml\nchunkSize: 2\n" + extra);
	}

	private static void initialize(JDBCDataSource source) throws Exception {
		sql(source,
				"CREATE TABLE CUSTOMERS(ID INTEGER NOT NULL PRIMARY KEY,NAME VARCHAR(40),STATUS CHAR(1),NOTE VARCHAR(80))",
				"CREATE TABLE EVENTS(TXT VARCHAR(20))",
				"INSERT INTO CUSTOMERS VALUES(1,' alice ','A','n1'),(2,'bob','I',NULL),(3,NULL,NULL,'n3')",
				"INSERT INTO EVENTS VALUES('a'),(''),(NULL)");
	}

	private static List<String> rows(JDBCDataSource ds, String query) throws Exception {
		var result = new ArrayList<String>();
		try (var c = ds.getConnection(); var s = c.createStatement(); var r = s.executeQuery(query)) {
			int count = r.getMetaData().getColumnCount();
			while (r.next()) {
				var values = new ArrayList<String>();
				for (int i = 1; i <= count; i++)
					values.add(String.valueOf(r.getString(i)));
				result.add(String.join("|", values));
			}
		}
		return result;
	}

	private static void sql(JDBCDataSource ds, String... commands) throws Exception {
		try (var c = ds.getConnection(); var s = c.createStatement()) {
			for (String command : commands)
				s.execute(command);
		}
	}

	private static JDBCDataSource database() {
		var ds = new JDBCDataSource();
		ds.setUrl("jdbc:hsqldb:mem:direct_conversions_" + UUID.randomUUID());
		ds.setUser("SA");
		ds.setPassword("");
		return ds;
	}
}
