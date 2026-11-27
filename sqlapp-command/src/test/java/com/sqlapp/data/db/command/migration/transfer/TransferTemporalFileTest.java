/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;

class TransferTemporalFileTest {
	@TempDir
	Path directory;

	@ParameterizedTest
	@ValueSource(strings = { "UTC", "Asia/Tokyo", "America/New_York" })
	void createsAndVerifiesTemporalTablesWithNullsAndNanoseconds(String zone) throws Exception {
		final var original = TimeZone.getDefault();
		try {
			TimeZone.setDefault(TimeZone.getTimeZone(zone));
			final var source = database();
			final var target = database();
			sql(source, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY, D DATE, T TIME(0), TS TIMESTAMP(9))",
					"INSERT INTO ITEMS VALUES(1,DATE '2024-02-29',TIME '23:59:59',TIMESTAMP '2024-02-29 23:59:59.123456789')",
					"INSERT INTO ITEMS VALUES(2,NULL,NULL,NULL)");
			final var exported = export(source, "CREATE_MISSING");
			final String csv = Files
					.readString(exported.file().getParent().resolve(exported.manifest().tables().getFirst().file()));
			assertTrue(csv.contains("2024-02-29T23:59:59.123456789"));
			assertTrue(csv.contains("\"23:59:59\""));
			TimeZone.setDefault(TimeZone.getTimeZone("UTC".equals(zone) ? "Asia/Tokyo" : "UTC"));
			final var service = new TransferFileImportService();
			final var bindings = Map.of("to", target);
			final var result = service.execute(service.review(exported.file(), exported.fingerprint(), bindings),
					bindings, directory);
			assertEquals(TransferJobExecutionService.Phase.COMPLETE, result.phase());
			assertTrue(result.verification().values().stream().allMatch(value -> value.isMatch()));
			try (var connection = target.getConnection();
					var sql = connection.createStatement();
					var rows = sql.executeQuery("SELECT D,T,TS FROM ITEMS ORDER BY ID")) {
				assertTrue(rows.next());
				assertEquals(java.time.LocalDate.of(2024, 2, 29), rows.getObject(1, java.time.LocalDate.class));
				assertEquals(java.time.LocalTime.of(23, 59, 59), rows.getObject(2, java.time.LocalTime.class));
				assertEquals(java.time.LocalDateTime.parse("2024-02-29T23:59:59.123456789"),
						rows.getObject(3, java.time.LocalDateTime.class));
				assertTrue(rows.next());
				assertNull(rows.getObject(1));
				assertNull(rows.getObject(2));
				assertNull(rows.getObject(3));
			}
		} finally {
			TimeZone.setDefault(original);
		}
	}

	@Test
	void targetPrecisionLossFailsVerificationAndRetainsCommittedRows() throws Exception {
		final var source = database();
		final var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY, TS TIMESTAMP(9))",
				"INSERT INTO ITEMS VALUES(1,TIMESTAMP '2024-01-01 12:00:00.123456789')");
		sql(target, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY, TS TIMESTAMP(3))");
		final var exported = export(source, "REQUIRE_EXISTING");
		final var bindings = Map.of("to", target);
		final var service = new TransferFileImportService();
		final var review = service.review(exported.file(), exported.fingerprint(), bindings);
		final var failure = assertThrows(TransferFileImportService.ImportException.class,
				() -> service.execute(review, bindings, directory));
		assertEquals(TransferJobExecutionService.Phase.VERIFICATION, failure.outcome().phase());
		assertEquals(1, failure.outcome().copyResult().getProcessedRows());
		assertTrue(Files.isRegularFile(failure.outcome().auditFile()));
	}

	@Test
	void invalidTemporalCsvFailsBeforeBorrowingTheTargetEvenWithUpdatedHashes() throws Exception {
		final var source = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY, D DATE)",
				"INSERT INTO ITEMS VALUES(1,DATE '2024-02-29')");
		final var exported = export(source, "REQUIRE_EXISTING");
		final var member = exported.manifest().tables().getFirst();
		final var file = exported.file().getParent().resolve(member.file());
		Files.writeString(file, "\"ID\",\"D\"\n\"1\",\"2024-02-30\"\n");
		final var changed = new TransferFilePackageIO.TableFile(member.taskId(), member.catalog(), member.schema(),
				member.table(), member.columns(), member.file(), member.rows(), Files.size(file),
				com.sqlapp.data.db.command.migration.internal.BoundedMigrationFile.sha256(file, null, "limit", "CSV"));
		Files.writeString(exported.file(),
				new com.sqlapp.util.JsonConverter().toJsonString(new TransferFilePackageIO.Manifest(1,
						exported.manifest().jobFingerprint(), exported.manifest().schemaFingerprint(),
						exported.manifest().files(), List.of(changed))));
		final var borrowed = new java.util.concurrent.atomic.AtomicInteger();
		final var target = new JDBCDataSource() {
			@Override
			public java.sql.Connection getConnection() throws java.sql.SQLException {
				borrowed.incrementAndGet();
				throw new java.sql.SQLException("Must not borrow for invalid CSV");
			}
		};
		assertThrows(Exception.class,
				() -> new TransferFileImportService().review(exported.file(), null, Map.of("to", target)));
		assertEquals(0, borrowed.get());
	}

	private TransferFilePackageIO.Snapshot export(JDBCDataSource source, String schemaMode) throws Exception {
		try (var connection = source.getConnection()) {
			final var schema = TransferJobPreparationService.readSchema(connection,
					TransferJobPreparationService.dialect(connection), null);
			for (var column : schema.getTables().get("ITEMS").getColumns())
				if (TransferCsvTemporalCodec.temporal(column))
					assertDoesNotThrow(() -> TransferCsvTemporalCodec.validate(column),
							column.getName() + ": " + column.getDataType() + "/" + column.getScale());
		}
		final var job = Files.writeString(directory.resolve("transfer.yaml"),
				"formatVersion: 1\nsource: from\ntarget: to\ntables: [ITEMS]\nschemaMode: " + schemaMode
						+ "\ndataMode: FILE\nchunkSize: 1\nfiles: {directory: packages, nullValue: __NULL__}\n");
		return new TransferFileExportService().export(job, Map.of("from", source));
	}

	private static JDBCDataSource database() {
		final var result = new JDBCDataSource();
		result.setUrl("jdbc:hsqldb:mem:temporal_transfer_" + UUID.randomUUID());
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
