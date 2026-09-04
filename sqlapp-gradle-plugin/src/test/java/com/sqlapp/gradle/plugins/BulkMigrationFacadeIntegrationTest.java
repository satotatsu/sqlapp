/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.migration.BulkMigration;
import com.sqlapp.data.db.command.migration.BulkMigrationOperationalReportIO;
import com.sqlapp.data.db.command.migration.BulkMigrationTableOption;
import com.sqlapp.data.db.command.migration.BulkMigrationVerificationReportIO;
import com.sqlapp.data.db.command.migration.BulkMigrationVerificationMismatchException;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTaskState;
import com.sqlapp.jdbc.bulk.BulkMigrationJobListener;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationListener;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationProgress;

class BulkMigrationFacadeIntegrationTest {
	@TempDir
	Path directory;

	@Test
	void executesUpsertAndVerifiesThroughTheFacade() throws Exception {
		final DataSource source = sqlite(directory.resolve("source.db"));
		final DataSource target = sqlite(directory.resolve("target.db"));
		final List<String> events = new ArrayList<>();
		final Path verificationReport = directory.resolve("verification/report.json");
		try (var connection = source.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS (ID INTEGER NOT NULL PRIMARY KEY, TXT TEXT)");
			statement.execute("INSERT INTO ITEMS VALUES (1, 'one'), (2, 'two')");
		}
		try (var connection = target.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS (ID INTEGER NOT NULL PRIMARY KEY, TXT TEXT)");
			statement.execute("INSERT INTO ITEMS VALUES (1, 'old')");
		}
		final BulkMigration migration = BulkMigration.builder().source(source).target(target)
				.schema(schema()).tables("ITEMS").resume(true)
				.tableOption("ITEMS", BulkMigrationTableOption.builder()
						.migrationId("items-copy").chunkSize(1).build())
				.jobListener(new BulkMigrationJobListener() {
					@Override
					public void onTaskStarted(String taskId, int taskIndex, int taskCount) {
						events.add("task:" + taskId);
					}
				})
				.chunkListener(new ChunkedBulkMigrationListener() {
					@Override
					public void onChunkCompleted(ChunkedBulkMigrationProgress progress) {
						events.add("chunk:" + progress.getChunkIndex());
					}
				})
				.fingerprints("source-v1", "target-v1")
				.verificationReport(verificationReport).build();

		final var outcome = migration.executeAndVerifyOrThrow();

		assertEquals(2, outcome.migration().getProcessedRows());
		assertTrue(outcome.isMatch());
		assertEquals(outcome, outcome.requireMatch());
		final var report = new BulkMigrationVerificationReportIO().read(verificationReport);
		assertTrue(report.match());
		assertEquals(2, report.expectedRows());
		assertEquals(2, report.actualRows());
		assertEquals(0, report.mismatchedTasks());
		assertEquals("DEFAULT", report.isolation());
		assertEquals(List.of("task:ITEMS", "chunk:0", "chunk:1"), events);
		assertEquals(BulkMigrationJobTaskState.COMPLETE,
				migration.inspect().getTasks().get(0).getState());
		assertEquals("items-copy", migration.inspect().getTasks().get(0)
				.getCheckpoint().getMigrationId());
		final var resumed = migration.execute();
		assertEquals(0, resumed.getProcessedRows());
		assertEquals(1, resumed.getAlreadyCompleteTasks());
		try (var connection = target.getConnection(); var statement = connection.createStatement();
				var rows = statement.executeQuery("SELECT ID, TXT FROM ITEMS ORDER BY ID")) {
			rows.next();
			assertEquals(1, rows.getInt(1));
			assertEquals("one", rows.getString(2));
			rows.next();
			assertEquals(2, rows.getInt(1));
			assertEquals("two", rows.getString(2));
		}
	}

	@Test
	void resumesFromFileCheckpointsWithoutCreatingATargetCheckpointTable()
			throws Exception {
		final DataSource source = sqlite(directory.resolve("file-source.db"));
		final DataSource target = sqlite(directory.resolve("file-target.db"));
		try (var connection = source.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS (ID INTEGER NOT NULL PRIMARY KEY, TXT TEXT)");
			statement.execute("INSERT INTO ITEMS VALUES (1, 'one'), (2, 'two')");
		}
		try (var connection = target.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS (ID INTEGER NOT NULL PRIMARY KEY, TXT TEXT)");
		}
		final Path checkpoints = directory.resolve("checkpoints");
		final BulkMigration migration = BulkMigration.builder().source(source).target(target)
				.schema(schema()).tables("ITEMS").resume(true).chunkSize(1)
				.fingerprints("source-v1", "target-v1")
				.fileCheckpoints(checkpoints).build();

		assertEquals(BulkMigrationJobTaskState.NOT_STARTED,
				migration.inspect().getTasks().get(0).getState());
		assertFalse(Files.exists(checkpoints));
		assertEquals(2, migration.execute().getProcessedRows());
		try (var files = Files.list(checkpoints)) {
			assertEquals(1, files.count());
		}
		assertEquals(BulkMigrationJobTaskState.COMPLETE,
				migration.inspect().getTasks().get(0).getState());
		assertEquals(1, migration.execute().getAlreadyCompleteTasks());
		try (var connection = target.getConnection(); var statement = connection.createStatement();
				var rows = statement.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'SQLAPP_BULK_MIGRATION_CHECKPOINT'")) {
			rows.next();
			assertEquals(0, rows.getInt(1));
		}
	}

	@Test
	void executesWithAnOptionalFileLease() throws Exception {
		final DataSource source = sqlite(directory.resolve("lease-source.db"));
		final DataSource target = sqlite(directory.resolve("lease-target.db"));
		try (var connection = source.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS (ID INTEGER NOT NULL PRIMARY KEY, TXT TEXT)");
			statement.execute("INSERT INTO ITEMS VALUES (1, 'one')");
		}
		try (var connection = target.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS (ID INTEGER NOT NULL PRIMARY KEY, TXT TEXT)");
		}
		final Path leases = directory.resolve("leases");
		final Path reportFile = directory.resolve("reports/operation.json");
		final BulkMigration migration = BulkMigration.builder().source(source).target(target)
				.schema(schema()).tables("ITEMS").fileLease("integration-worker", leases)
				.operationalReport(reportFile)
				.build();

		assertEquals(1, migration.execute().getProcessedRows());
		assertTrue(Files.isDirectory(leases));
		try (var files = Files.list(leases)) {
			assertTrue(files.anyMatch(path -> path.getFileName().toString().endsWith(".lock")));
		}
		final var report = new BulkMigrationOperationalReportIO().read(reportFile);
		assertEquals("JOB_COMPLETED", report.execution().event());
		assertEquals(1, report.processedRows());
		assertEquals(1, report.completedTasks());
	}

	@Test
	void recordsVerificationMismatchAsTheFinalOperationalEvent() throws Exception {
		final DataSource source = sqlite(directory.resolve("mismatch-source.db"));
		final DataSource target = sqlite(directory.resolve("mismatch-target.db"));
		for (final DataSource dataSource : List.of(source, target)) {
			try (var connection = dataSource.getConnection();
					var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE ITEMS (ID INTEGER NOT NULL PRIMARY KEY, TXT TEXT)");
			}
		}
		try (var connection = target.getConnection(); var statement = connection.createStatement()) {
			statement.execute("INSERT INTO ITEMS VALUES (9, 'target only')");
		}
		final Path operational = directory.resolve("mismatch/operation.json");
		final Path verification = directory.resolve("mismatch/verification.json");
		final BulkMigration migration = BulkMigration.builder().source(source).target(target)
				.schema(schema()).tables("ITEMS").operationalReport(operational)
				.verificationReport(verification).build();

		org.junit.jupiter.api.Assertions.assertThrows(
				BulkMigrationVerificationMismatchException.class,
				migration::executeAndVerifyOrThrow);
		final var operationalReport = new BulkMigrationOperationalReportIO()
				.read(operational);
		assertEquals("JOB_FAILED", operationalReport.execution().event());
		assertEquals(BulkMigrationVerificationMismatchException.class.getName(),
				operationalReport.execution().failureType());
		assertFalse(new BulkMigrationVerificationReportIO().read(verification).match());
	}

	private static Schema schema() {
		final Schema schema = new Schema();
		final Table table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add(new Column("TXT").setDataType(DataType.CLOB));
		table.setPrimaryKey("PK_ITEMS", table.getColumns().get("ID"));
		schema.getTables().add(table);
		return schema;
	}

	private static DataSource sqlite(final Path file) throws Exception {
		final Object value = Class.forName("org.sqlite.SQLiteDataSource")
				.getConstructor().newInstance();
		value.getClass().getMethod("setUrl", String.class)
				.invoke(value, "jdbc:sqlite:" + file.toAbsolutePath());
		return (DataSource) value;
	}
}
