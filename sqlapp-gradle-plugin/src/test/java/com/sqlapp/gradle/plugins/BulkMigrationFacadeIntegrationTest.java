/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.migration.BulkMigration;
import com.sqlapp.data.db.command.migration.BulkMigrationJobRepairPlanReportIO;
import com.sqlapp.data.db.command.migration.BulkMigrationOperationalReportIO;
import com.sqlapp.data.db.command.migration.BulkMigrationPostExecutionException;
import com.sqlapp.data.db.command.migration.BulkMigrationTableOption;
import com.sqlapp.data.db.command.migration.BulkMigrationVerificationReportIO;
import com.sqlapp.data.db.command.migration.BulkMigrationVerificationMismatchException;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTaskState;
import com.sqlapp.jdbc.bulk.BulkMigrationJobListener;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationListener;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationProgress;

class BulkMigrationFacadeIntegrationTest {
	@TempDir
	Path directory;

	@Test
	void runsWithOnlySourceTargetAndSchema() throws Exception {
		final DataSource source = sqlite(directory.resolve("simple-source.db"));
		final DataSource target = sqlite(directory.resolve("simple-target.db"));
		try (var connection = source.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS (ID INTEGER NOT NULL PRIMARY KEY, TXT TEXT)");
			statement.execute("INSERT INTO ITEMS VALUES (1, 'one')");
		}
		try (var connection = target.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS (ID INTEGER NOT NULL PRIMARY KEY, TXT TEXT)");
		}

		final var result = BulkMigration.of(source, target, schema()).run();

		assertEquals(1, result.migration().getProcessedRows());
		assertTrue(result.isMatch());
	}

	@Test
	void retainsCommittedResultWhenPostExecutionVerificationReportingFails()
			throws Exception {
		final DataSource source = sqlite(directory.resolve("post-failure-source.db"));
		final DataSource target = sqlite(directory.resolve("post-failure-target.db"));
		try (var connection = source.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS (ID INTEGER NOT NULL PRIMARY KEY, TXT TEXT)");
			statement.execute("INSERT INTO ITEMS VALUES (1, 'one')");
		}
		try (var connection = target.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS (ID INTEGER NOT NULL PRIMARY KEY, TXT TEXT)");
		}
		final Path invalidReport = directory.resolve("post-failure/report-directory");
		final Path operational = directory.resolve("post-failure/operation.json");
		Files.createDirectories(invalidReport);
		final BulkMigration migration = BulkMigration.builder().source(source).target(target)
				.schema(schema()).verificationReport(invalidReport)
				.operationalReport(operational).build();

		final var failure = org.junit.jupiter.api.Assertions.assertThrows(
				BulkMigrationPostExecutionException.class, migration::run);

		assertEquals(1, failure.getMigrationResult().getProcessedRows());
		assertTrue(failure.getCause() instanceof CommandException);
		final var operationalReport = new BulkMigrationOperationalReportIO()
				.read(operational);
		assertEquals("JOB_FAILED", operationalReport.execution().event());
		assertEquals(BulkMigrationPostExecutionException.class.getName(),
				operationalReport.execution().failureType());
		try (var connection = target.getConnection(); var statement = connection.createStatement();
				var rows = statement.executeQuery("SELECT COUNT(*) FROM ITEMS")) {
			rows.next();
			assertEquals(1, rows.getInt(1));
		}
	}

	@Test
	void executesUpsertAndVerifiesThroughTheFacade() throws Exception {
		final DataSource source = sqlite(directory.resolve("source.db"));
		final DataSource target = sqlite(directory.resolve("target.db"));
		final List<String> events = new ArrayList<>();
		final Path verificationReport = directory.resolve("verification/report.json");
		final Path unexpectedRepairPlan = directory.resolve("verification/repair.json");
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
				.verificationReport(verificationReport)
				.repairPlanOnMismatch(unexpectedRepairPlan).build();

		final var outcome = migration.run();

		assertEquals(2, outcome.migration().getProcessedRows());
		assertTrue(outcome.isMatch());
		assertEquals(outcome, outcome.requireMatch());
		assertFalse(migration.verifyAndPlanRepair().isRequired());
		final var report = new BulkMigrationVerificationReportIO().read(verificationReport);
		assertTrue(report.match());
		assertEquals(2, report.expectedRows());
		assertEquals(2, report.actualRows());
		assertEquals(0, report.mismatchedTasks());
		assertFalse(Files.exists(unexpectedRepairPlan));
		assertEquals("DEFAULT", report.isolation());
		assertEquals(List.of("task:ITEMS", "chunk:0", "chunk:1"), events);
		assertEquals(BulkMigrationJobTaskState.COMPLETE,
				migration.status().getTasks().get(0).getState());
		assertEquals("items-copy", migration.status().getTasks().get(0)
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

		final Path dryRunReport = directory.resolve("file-dry-run.json");
		final var plan = migration.dryRun(dryRunReport);
		assertEquals(List.of("ITEMS"),
				plan.tasks().stream().map(task -> task.taskId()).toList());
		assertFalse(Files.exists(checkpoints));
		assertEquals(BulkMigrationJobTaskState.NOT_STARTED,
				migration.status().getTasks().get(0).getState());
		assertFalse(Files.exists(checkpoints));
		assertEquals(2, migration.execute().getProcessedRows());
		try (var files = Files.list(checkpoints)) {
			assertEquals(1, files.count());
		}
		assertEquals(BulkMigrationJobTaskState.COMPLETE,
				migration.status().getTasks().get(0).getState());
		assertEquals(1, migration.execute().getAlreadyCompleteTasks());
		assertThrows(IllegalArgumentException.class,
				() -> migration.resetCheckpointsWithFingerprint("wrong-fingerprint"));
		assertEquals(BulkMigrationJobTaskState.COMPLETE,
				migration.status().getTasks().get(0).getState());
		final var reset = migration.resetCheckpoints(dryRunReport);
		assertEquals(List.of("ITEMS"), reset.getResetTaskIds());
		assertEquals(BulkMigrationJobTaskState.NOT_STARTED,
				migration.status().getTasks().get(0).getState());
		try (var connection = target.getConnection(); var statement = connection.createStatement();
				var rows = statement.executeQuery("SELECT COUNT(*) FROM ITEMS")) {
			rows.next();
			assertEquals(2, rows.getInt(1));
		}
		assertEquals(2, migration.execute().getProcessedRows());
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
		final String approvedFingerprint = migration.dryRun().planFingerprint();
		assertEquals(List.of("ITEMS"),
				migration.resetCheckpointsWithFingerprint(approvedFingerprint)
						.getResetTaskIds());
		assertEquals(BulkMigrationJobTaskState.NOT_STARTED,
				migration.status().getTasks().get(0).getState());
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
		try (var connection = source.getConnection(); var statement = connection.createStatement()) {
			statement.execute("INSERT INTO ITEMS VALUES (1, 'source')");
		}
		try (var connection = target.getConnection(); var statement = connection.createStatement()) {
			statement.execute("INSERT INTO ITEMS VALUES (9, 'target only')");
		}
		final Path operational = directory.resolve("mismatch/operation.json");
		final Path verification = directory.resolve("mismatch/verification.json");
		final Path repairPlan = directory.resolve("mismatch/repair.json");
		final BulkMigration migration = BulkMigration.builder().source(source).target(target)
				.schema(schema()).tables("ITEMS").operationalReport(operational)
				.verificationReport(verification).repairPlanOnMismatch(repairPlan).build();

		final var mismatch = org.junit.jupiter.api.Assertions.assertThrows(
				BulkMigrationVerificationMismatchException.class,
				migration::run);
		assertTrue(mismatch.hasMigrationResult());
		assertEquals(1, mismatch.getMigrationResult().getProcessedRows());
		final var repair = new BulkMigrationJobRepairPlanReportIO()
				.read(repairPlan);
		assertEquals(1, repair.estimatedReplayRows());
		assertEquals(1, repair.mismatchChunks());
		final var operationalReport = new BulkMigrationOperationalReportIO()
				.read(operational);
		assertEquals("JOB_FAILED", operationalReport.execution().event());
		assertEquals(BulkMigrationVerificationMismatchException.class.getName(),
				operationalReport.execution().failureType());
		assertFalse(new BulkMigrationVerificationReportIO().read(verification).match());

		final Path invalidRepairTarget = directory.resolve("mismatch/repair-directory");
		Files.createDirectories(invalidRepairTarget);
		final BulkMigration invalidReportMigration = BulkMigration.builder().source(source)
				.target(target).schema(schema()).tables("ITEMS")
				.repairPlanOnMismatch(invalidRepairTarget).build();
		final var reportFailure = org.junit.jupiter.api.Assertions.assertThrows(
				BulkMigrationVerificationMismatchException.class,
				invalidReportMigration::run);
		assertTrue(reportFailure.hasMigrationResult());
		assertEquals(1, reportFailure.getMigrationResult().getProcessedRows());
		assertEquals(1, reportFailure.getSuppressed().length);
		assertTrue(reportFailure.getSuppressed()[0] instanceof CommandException);
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
