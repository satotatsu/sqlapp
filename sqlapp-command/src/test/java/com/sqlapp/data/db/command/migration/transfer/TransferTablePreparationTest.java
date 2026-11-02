/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static com.sqlapp.data.db.command.migration.transfer.TransferSchemaPreparation.TableAction.*;

class TransferTablePreparationTest {
	@TempDir
	Path directory;

	@Test
	void recordsMixedCreationAndExistingUseWithoutExecutingDdl() throws Exception {
		var source = database();
		var target = database();
		sql(source, "CREATE TABLE A(ID INT PRIMARY KEY)", "CREATE TABLE B(ID INT PRIMARY KEY)");
		sql(target, "CREATE TABLE A(ID INT PRIMARY KEY)");
		var review = review(source, target, "CREATE_MISSING", "", "[A, B]");
		var tables = review.prepared().schemaPreparation().tables();
		assertEquals(List.of("A", "B"),
				tables.stream().map(TransferSchemaPreparation.TablePreparation::targetTable).toList());
		assertEquals(List.of(USE_EXISTING, CREATE),
				tables.stream().map(TransferSchemaPreparation.TablePreparation::action).toList());
		assertEquals(List.of("transfer-table-1", "transfer-table-2"),
				tables.stream().map(TransferSchemaPreparation.TablePreparation::taskId).toList());
		assertTrue(tables.stream().allMatch(table -> "PUBLIC".equals(table.targetSchema())));
		assertThrows(UnsupportedOperationException.class, () -> tables.clear());
		try (var connection = target.getConnection();
				var objects = connection.getMetaData().getTables(null, "PUBLIC", "B", null)) {
			assertFalse(objects.next());
		}
		var report = new TransferJobReportIO().writeReview(review);
		assertTrue(Files.readString(report).contains("USE_EXISTING"));
		assertEquals(USE_EXISTING, review(source, target, "REQUIRE_EXISTING", "", "[A]").prepared().schemaPreparation()
				.tables().getFirst().action());
	}

	@Test
	void distinguishesSchemaOnlySkipFromDifferenceScopeAndRequiresFreshTargetReview() throws Exception {
		var source = database();
		var target = database();
		sql(source, "CREATE TABLE A(ID INT PRIMARY KEY)", "CREATE TABLE B(ID INT PRIMARY KEY)");
		sql(target, "CREATE TABLE A(ID INT PRIMARY KEY)");
		var create = review(source, target, "CREATE_MISSING", "dataMode: SCHEMA_ONLY\n", "[A, B]");
		assertEquals(List.of(SKIP_EXISTING, CREATE), create.prepared().schemaPreparation().tables().stream()
				.map(TransferSchemaPreparation.TablePreparation::action).toList());
		var difference = review(source, target, "APPLY_DIFFERENCES", "dataMode: SCHEMA_ONLY\n", "[A, B]");
		assertEquals(List.of(APPLY_DIFFERENCES, CREATE), difference.prepared().schemaPreparation().tables().stream()
				.map(TransferSchemaPreparation.TablePreparation::action).toList());
		sql(target, "CREATE TABLE B(ID INT PRIMARY KEY)");
		var refreshed = review(source, target, "CREATE_MISSING", "dataMode: SCHEMA_ONLY\n", "[A, B]");
		assertNotEquals(create.prepared().schemaPreparation().fingerprint(),
				refreshed.prepared().schemaPreparation().fingerprint());
		assertEquals(List.of(SKIP_EXISTING, SKIP_EXISTING), refreshed.prepared().schemaPreparation().tables().stream()
				.map(TransferSchemaPreparation.TablePreparation::action).toList());
	}

	@Test
	void retainsMappedTargetEvidenceThroughKeylessWarningAndLegacyConstructors() throws Exception {
		var source = database();
		var target = database();
		sql(source, "CREATE TABLE A(NAME VARCHAR(20))");
		Files.writeString(directory.resolve("names.yaml"), "formatVersion: 1\ntables:\n  A: {targetTable: 履歴}\n");
		var mapped = review(source, target, "CREATE_MISSING", "mappingFile: names.yaml\n", "[A]");
		assertEquals("履歴", mapped.prepared().schemaPreparation().tables().getFirst().targetTable());
		assertEquals(CREATE, mapped.prepared().schemaPreparation().tables().getFirst().action());
		assertTrue(mapped.prepared().warnings().stream().anyMatch(w -> w.startsWith(TransferKeylessChecks.CODE)));
		assertTrue(new TransferSchemaPreparation.Plan("old", List.of()).tables().isEmpty());
		assertTrue(new TransferSchemaPreparation.Plan("old", List.of(), List.of()).tables().isEmpty());
	}

	private TransferJobExecutionService.Review review(JDBCDataSource source, JDBCDataSource target, String mode,
			String extra, String tables) throws Exception {
		var job = Files.writeString(directory.resolve("job.yaml"),
				"formatVersion: 1\nsource: from\ntarget: to\nschemaMode: " + mode + "\ntables: " + tables + "\n"
						+ extra);
		return new TransferJobExecutionService().review(job, Map.of("from", source, "to", target), directory);
	}

	private static JDBCDataSource database() {
		var result = new JDBCDataSource();
		result.setUrl("jdbc:hsqldb:mem:table_review_" + UUID.randomUUID());
		result.setUser("SA");
		result.setPassword("");
		return result;
	}

	private static void sql(JDBCDataSource source, String... statements) throws Exception {
		try (var connection = source.getConnection(); var statement = connection.createStatement()) {
			for (String sql : statements)
				statement.execute(sql);
		}
	}
}
