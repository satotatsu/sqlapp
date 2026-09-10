/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTaskState;
import com.sqlapp.jdbc.bulk.BulkMigrationJobLifecycle;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlan;
import com.sqlapp.jdbc.bulk.BulkMigrationJobResult;
import com.sqlapp.jdbc.bulk.BulkMigrationMode;
import com.sqlapp.jdbc.bulk.BulkMigrationMaintenanceState;
import com.sqlapp.jdbc.bulk.BulkMigrationMaintenanceStatus;
import com.sqlapp.jdbc.bulk.InMemoryBulkMigrationCheckpointStore;

class BulkMigrationTest {
	@TempDir
	Path directory;

	@Test
	void verifiesAndPlansRepairThroughTheSimpleFacade() throws Exception {
		final JDBCDataSource source = dataSource("facade_source");
		final JDBCDataSource target = dataSource("facade_target");
		try (var connection = source.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS (ID INTEGER NOT NULL PRIMARY KEY, TXT VARCHAR(20))");
		}
		try (var connection = target.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS (ID INTEGER NOT NULL PRIMARY KEY, TXT VARCHAR(20))");
			statement.execute("INSERT INTO ITEMS VALUES (1, 'target only')");
		}
		final Schema schema = new Schema("PUBLIC");
		final Table table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add(new Column("TXT").setDataType(DataType.VARCHAR).setLength(20));
		table.setPrimaryKey("PK_ITEMS", table.getColumns().get("ID"));
		schema.getTables().add(table);
		final BulkMigration migration = BulkMigration.builder().source(source).target(target)
				.schema(schema).tables("ITEMS").chunkSize(1)
				.verificationReport(directory.resolve("verification/mismatch.json")).build();

		final BulkMigrationOperationalReport dryRun = migration.dryRun();
		assertEquals(List.of("PUBLIC.ITEMS"),
				dryRun.tasks().stream().map(BulkMigrationOperationalReport.Task::taskId)
						.toList());
		assertEquals("NOT_STARTED", dryRun.tasks().get(0).state());
		assertEquals(BulkMigrationJobTaskState.NOT_STARTED,
				migration.status().getTasks().get(0).getState());
		final Path statusFile = directory.resolve("status/initial.json");
		final var statusReport = migration.dryRun(statusFile);
		assertEquals("NOT_STARTED", statusReport.tasks().get(0).state());
		assertEquals(statusReport, new BulkMigrationOperationalReportIO().read(statusFile));
		assertThrows(IllegalArgumentException.class,
				() -> migration.resetCheckpointsWithFingerprint("wrong-fingerprint"));
		try (var connection = target.getConnection(); var tables = connection.getMetaData()
				.getTables(connection.getCatalog(), null,
						"SQLAPP_BULK_MIGRATION_CHECKPOINT", new String[] { "TABLE" })) {
			assertFalse(tables.next());
		}
		final var mismatch = assertThrows(BulkMigrationVerificationMismatchException.class,
				migration::verifyOrThrow);
		final var verification = mismatch.getVerificationResult();

		assertFalse(mismatch.hasMigrationResult());
		assertNull(mismatch.getMigrationResult());
		assertFalse(verification.isMatch());
		assertTrue(Files.isRegularFile(directory.resolve("verification/mismatch.json")));
		assertFalse(new BulkMigrationVerificationReportIO()
				.read(directory.resolve("verification/mismatch.json")).match());
		assertEquals(0, verification.getExpectedRows());
		assertEquals(1, verification.getActualRows());
		final Path repairFile = directory.resolve("repair.json");
		final var repair = migration.verifyAndWriteRepairPlan(repairFile);
		assertTrue(repair.isRequired());
		assertFalse(repair.getVerificationResult().isMatch());
		final var report = new BulkMigrationJobRepairPlanReportIO().read(repairFile);
		assertEquals(0, report.estimatedReplayRows());
		assertEquals(1, report.mismatchChunks());
		final var result = repair.executeApproved(repairFile);
		assertEquals(0, result.getReplayedRows());
		assertEquals(List.of(0L), result.getTasks().get(0).getRepairResult()
				.getChunksWithoutExpectedRows());
	}

	@Test
	void keepsResumeExplicitBecauseSchemaIsNotADataFingerprint() {
		final Schema schema = new Schema("PUBLIC");
		final Table table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setNotNull(true));
		table.setPrimaryKey("PK_ITEMS", table.getColumns().get("ID"));
		schema.getTables().add(table);
		assertThrows(NullPointerException.class,
				() -> BulkMigration.of(null, dataSource("of_target"), schema));
		assertThrows(NullPointerException.class,
				() -> BulkMigration.of(dataSource("of_source"), null, schema));
		assertThrows(NullPointerException.class, () -> BulkMigration.of(
				dataSource("of_schema_source"), dataSource("of_schema_target"), null));
		assertThrows(IllegalArgumentException.class, () -> BulkMigration.builder()
				.source(dataSource("invalid_source")).target(dataSource("invalid_target"))
				.schema(schema).resume(true).build());
		assertThrows(IllegalArgumentException.class, () -> BulkMigration.builder()
				.source(dataSource("invalid_file_source"))
				.target(dataSource("invalid_file_target")).schema(schema)
				.fileCheckpoints(null).build());
		assertThrows(IllegalArgumentException.class, () -> BulkMigration.builder()
				.source(dataSource("invalid_custom_source"))
				.target(dataSource("invalid_custom_target")).schema(schema)
				.customCheckpointStore(null).build());
		assertThrows(IllegalArgumentException.class, () -> BulkMigration.builder()
				.source(dataSource("invalid_lease_source"))
				.target(dataSource("invalid_lease_target")).schema(schema)
				.fileLease("worker", null));
		assertThrows(NullPointerException.class, () -> BulkMigration.builder()
				.operationalReport(null));
		assertThrows(NullPointerException.class, () -> BulkMigration.builder()
				.verificationReport(null));
		assertThrows(NullPointerException.class, () -> BulkMigration.builder()
				.repairPlanOnMismatch(null));
		assertThrows(IllegalArgumentException.class, () -> BulkMigration.builder()
				.source(dataSource("invalid_report_source"))
				.target(dataSource("invalid_report_target")).schema(schema)
				.verificationReport(directory.resolve("verification.json"), 0).build());
		assertThrows(IllegalArgumentException.class, () -> BulkMigration.builder()
				.source(dataSource("invalid_maintenance_source"))
				.target(dataSource("invalid_maintenance_target")).schema(schema)
				.databaseMaintenance("bad.table").build());
		assertThrows(IllegalArgumentException.class, () -> BulkMigration.builder()
				.source(dataSource("conflicting_maintenance_source"))
				.target(dataSource("conflicting_maintenance_target")).schema(schema)
				.maintenanceDirectory(directory).maintenanceTableName("MAINTENANCE")
				.build());

		final var customStore = new InMemoryBulkMigrationCheckpointStore();
		final BulkMigration custom = BulkMigration.builder()
				.source(dataSource("custom_source")).target(dataSource("custom_target"))
				.schema(schema).customCheckpointStore(customStore).build();
		assertThrows(NullPointerException.class, () -> custom.dryRun(null));
		assertThrows(NullPointerException.class,
				() -> custom.verifyAndWriteRepairPlan(null));
		assertThrows(IllegalArgumentException.class,
				() -> custom.resetCheckpointsWithFingerprint(null));
		assertThrows(IllegalArgumentException.class,
				() -> custom.resetCheckpointsWithFingerprint(" "));
		assertThrows(IllegalStateException.class,
				() -> custom.recoverMaintenanceWithFingerprint("approved"));
		assertThrows(IllegalArgumentException.class,
				() -> custom.recoverMaintenanceWithFingerprint(" "));
		assertThrows(NullPointerException.class,
				() -> custom.recoverMaintenance(null));
		assertThrows(NullPointerException.class,
				() -> custom.resetCheckpoints((Path) null));
		assertEquals(BulkMigrationJobTaskState.NOT_STARTED,
				assertDoesNotThrow(() -> custom.status()).getTasks().get(0).getState());
	}

	@Test
	void appliesOnlyTheRequestedAdvancedTableOverrides() throws Exception {
		final JDBCDataSource source = dataSource("facade_override_source");
		final JDBCDataSource target = dataSource("facade_override_target");
		for (final JDBCDataSource dataSource : List.of(source, target)) {
			try (var connection = dataSource.getConnection();
					var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE ITEMS (ID INTEGER NOT NULL PRIMARY KEY, TXT VARCHAR(20))");
			}
		}
		try (var connection = source.getConnection(); var statement = connection.createStatement()) {
			statement.execute("INSERT INTO ITEMS VALUES (1, 'source'), (2, 'source two')");
		}
		try (var connection = target.getConnection(); var statement = connection.createStatement()) {
			statement.execute("INSERT INTO ITEMS VALUES (1, 'different'), (2, 'also different')");
		}
		final Schema schema = new Schema("PUBLIC");
		final Table table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add(new Column("TXT").setDataType(DataType.VARCHAR).setLength(20));
		table.setPrimaryKey("PK_ITEMS", table.getColumns().get("ID"));
		schema.getTables().add(table);
		final BulkMigration migration = BulkMigration.builder().source(source).target(target)
				.schema(schema).tableOption("ITEMS", BulkMigrationTableOption.builder()
						.verificationColumns(List.of("ID")).chunkSize(1).build())
				.verificationIsolation(BulkMigrationVerificationIsolation.REPEATABLE_READ)
				.build();

		final var verification = migration.verify();

		assertEquals(List.of("ID"), verification.getTasks().get(0).getColumns());
		assertTrue(verification.isMatch());
		assertEquals(1, verification.getTasks().get(0).getVerificationResult()
				.getChunkSize());
		assertEquals(2, verification.getTasks().get(0).getVerificationResult()
				.getChunks().size());
		assertThrows(IllegalArgumentException.class, () -> BulkMigration.builder()
				.source(source).target(target).schema(schema).tableOption("MISSING",
						BulkMigrationTableOption.defaults()).build());
		assertThrows(IllegalArgumentException.class, () -> BulkMigration.builder()
				.source(source).target(target).schema(schema).tableOption("ITEMS",
						BulkMigrationTableOption.builder().verificationChunkSize(0).build())
				.build());
	}

	@Test
	void executesWithAnOptionalDatabaseLeaseOnASeparateConnection() throws Exception {
		final JDBCDataSource source = dataSource("facade_database_lease_source");
		final JDBCDataSource target = dataSource("facade_database_lease_target");
		try (var connection = source.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS (ID INTEGER NOT NULL PRIMARY KEY, TXT VARCHAR(20))");
		}
		try (var connection = target.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS (ID INTEGER NOT NULL PRIMARY KEY, TXT VARCHAR(20))");
		}
		final Schema schema = new Schema("PUBLIC");
		final Table table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add(new Column("TXT").setDataType(DataType.VARCHAR).setLength(20));
		table.setPrimaryKey("PK_ITEMS", table.getColumns().get("ID"));
		schema.getTables().add(table);
		final BulkMigration migration = BulkMigration.builder().source(source).target(target)
				.schema(schema).tables("ITEMS").mode(BulkMigrationMode.INSERT)
				.databaseLease("database-worker").build();

		assertEquals(BulkMigrationResumeReadiness.RESUMABLE,
				migration.resumeReadiness());
		try (var connection = target.getConnection(); var tables = connection.getMetaData()
				.getTables(connection.getCatalog(), null, "sqlapp_bulk_job_lease",
						new String[] { "TABLE" })) {
			assertFalse(tables.next());
		}
		assertEquals(0, migration.execute().getProcessedRows());
		final String approvedFingerprint = migration.dryRun().planFingerprint();
		assertEquals(List.of("PUBLIC.ITEMS"),
				migration.resetCheckpointsWithFingerprint(approvedFingerprint)
						.getResetTaskIds());
		assertEquals(BulkMigrationJobTaskState.NOT_STARTED,
				migration.status().getTasks().get(0).getState());
		try (var connection = target.getConnection(); var tables = connection.getMetaData()
				.getTables(connection.getCatalog(), null, "sqlapp_bulk_job_lease",
						new String[] { "TABLE" })) {
			assertTrue(tables.next());
		}
	}

	@Test
	void invokesAnOptionalLifecycleOnlyDuringExecution() throws Exception {
		final JDBCDataSource source = dataSource("facade_lifecycle_source");
		final JDBCDataSource target = dataSource("facade_lifecycle_target");
		for (final JDBCDataSource dataSource : List.of(source, target)) {
			try (var connection = dataSource.getConnection();
					var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE ITEMS (ID INTEGER NOT NULL PRIMARY KEY)");
			}
		}
		final Schema schema = new Schema("PUBLIC");
		final Table table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		table.setPrimaryKey("PK_ITEMS", table.getColumns().get("ID"));
		schema.getTables().add(table);
		final List<String> events = new ArrayList<>();
		final BulkMigrationJobLifecycle lifecycle = new BulkMigrationJobLifecycle() {
			@Override
			public String getConfigurationFingerprint() {
				return "test-lifecycle-v1";
			}

			@Override
			public void before(final Connection connection,
					final BulkMigrationJobPlan plan) throws SQLException {
				events.add("before");
			}

			@Override
			public void after(final Connection connection, final BulkMigrationJobPlan plan,
					final BulkMigrationJobResult result) throws SQLException {
				events.add("after");
			}
		};
		final BulkMigration migration = BulkMigration.builder().source(source).target(target)
				.schema(schema).mode(BulkMigrationMode.INSERT).lifecycle(lifecycle).build();

		assertEquals(BulkMigrationJobTaskState.NOT_STARTED,
				migration.status().getTasks().get(0).getState());
		assertTrue(events.isEmpty());
		migration.execute();
		assertEquals(List.of("before", "after"), events);
	}

	@Test
	void fileMaintenanceIsSimpleDurableAndVisibleInDryRun(
			@org.junit.jupiter.api.io.TempDir final java.nio.file.Path directory)
			throws Exception {
		final JDBCDataSource source = dataSource("facade_maintenance_source");
		final JDBCDataSource target = dataSource("facade_maintenance_target");
		for (final JDBCDataSource dataSource : List.of(source, target)) {
			try (var connection = dataSource.getConnection();
					var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE ITEMS (ID INTEGER NOT NULL PRIMARY KEY)");
			}
		}
		final Schema schema = new Schema("PUBLIC");
		final Table table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		table.setPrimaryKey("PK_ITEMS", table.getColumns().get("ID"));
		schema.getTables().add(table);
		final BulkMigration migration = BulkMigration.builder().source(source).target(target)
				.schema(schema).mode(BulkMigrationMode.INSERT)
				.fileMaintenance(directory)
				.operationalReport(directory.resolve("running.json")).build();

		assertEquals(null, migration.dryRun().maintenance());
		assertEquals(0, migration.execute().getProcessedRows());
		final var report = migration.dryRun();
		assertEquals(com.sqlapp.jdbc.bulk.BulkMigrationMaintenanceStatus.COMPLETE.name(),
				report.maintenance().status());
		assertEquals(BulkMigrationJobTaskState.COMPLETE,
				migration.status().getTasks().get(0).getState());
		final var published = new BulkMigrationOperationalReportIO()
				.read(directory.resolve("running.json"));
		assertEquals(BulkMigrationMaintenanceStatus.COMPLETE.name(),
				published.maintenance().status());
		assertEquals("JOB_COMPLETED", published.execution().event());
	}

	@Test
	void databaseMaintenanceUsesReadOnlyInspectionAndDedicatedPersistence()
			throws Exception {
		final JDBCDataSource source = dataSource("facade_database_maintenance_source");
		final JDBCDataSource target = dataSource("facade_database_maintenance_target");
		for (final JDBCDataSource dataSource : List.of(source, target)) {
			try (var connection = dataSource.getConnection();
					var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE ITEMS (ID INTEGER NOT NULL PRIMARY KEY)");
			}
		}
		final Schema schema = new Schema("PUBLIC");
		final Table table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		table.setPrimaryKey("PK_ITEMS", table.getColumns().get("ID"));
		schema.getTables().add(table);
		final BulkMigration migration = BulkMigration.builder().source(source).target(target)
				.schema(schema).mode(BulkMigrationMode.INSERT)
				.databaseMaintenance("SQLAPP_FACADE_MAINTENANCE").build();

		assertNull(migration.dryRun().maintenance());
		try (var connection = target.getConnection()) {
			assertFalse(tableExists(connection, "SQLAPP_FACADE_MAINTENANCE"));
		}
		assertEquals(0, migration.execute().getProcessedRows());
		assertEquals(com.sqlapp.jdbc.bulk.BulkMigrationMaintenanceStatus.COMPLETE.name(),
				migration.dryRun().maintenance().status());
		try (var connection = target.getConnection()) {
			assertTrue(tableExists(connection, "SQLAPP_FACADE_MAINTENANCE"));
		}

		final BulkMigrationJobLifecycle failingLifecycle = new BulkMigrationJobLifecycle() {
			@Override
			public String getConfigurationFingerprint() {
				return "failing-maintenance-v1";
			}

			@Override
			public void before(final Connection connection,
					final BulkMigrationJobPlan plan) throws SQLException {
				connection.setAutoCommit(false);
				throw new SQLException("preparation failed");
			}
		};
		final BulkMigration failing = BulkMigration.builder().source(source).target(target)
				.schema(schema).mode(BulkMigrationMode.INSERT)
				.lifecycle(failingLifecycle)
				.databaseMaintenance("SQLAPP_FACADE_MAINTENANCE")
				.operationalReport(directory.resolve("database-failed.json")).build();
		assertThrows(SQLException.class, failing::execute);
		assertEquals(com.sqlapp.jdbc.bulk.BulkMigrationMaintenanceStatus.RESTORED.name(),
				failing.dryRun().maintenance().status());
		final var failedReport = new BulkMigrationOperationalReportIO()
				.read(directory.resolve("database-failed.json"));
		assertEquals(BulkMigrationMaintenanceStatus.RESTORED.name(),
				failedReport.maintenance().status());
		assertEquals("JOB_FAILED", failedReport.execution().event());
	}

	@Test
	void recoversOnlyExplicitlyApprovedInterruptedMaintenance() throws Exception {
		final JDBCDataSource source = dataSource("facade_recovery_source");
		final JDBCDataSource target = dataSource("facade_recovery_target");
		for (final JDBCDataSource dataSource : List.of(source, target)) {
			try (var connection = dataSource.getConnection();
					var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE ITEMS (ID INTEGER NOT NULL PRIMARY KEY)");
			}
		}
		final Schema schema = new Schema("PUBLIC");
		final Table table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		table.setPrimaryKey("PK_ITEMS", table.getColumns().get("ID"));
		schema.getTables().add(table);
		final AtomicInteger restores = new AtomicInteger();
		final BulkMigrationJobLifecycle lifecycle = new BulkMigrationJobLifecycle() {
			@Override
			public String getConfigurationFingerprint() {
				return "recoverable-maintenance-v1";
			}

			@Override
			public void restore(final Connection connection,
					final BulkMigrationJobPlan plan, final Throwable failure) {
				restores.incrementAndGet();
			}
		};
		final Path maintenance = directory.resolve("maintenance");
		final BulkMigration migration = BulkMigration.builder().source(source).target(target)
				.schema(schema).mode(BulkMigrationMode.INSERT).lifecycle(lifecycle)
				.fileMaintenance(maintenance).build();
		final Path reportFile = directory.resolve("approved.json");
		final String fingerprint = migration.dryRun(reportFile).planFingerprint();
		new FileBulkMigrationMaintenanceStateStore(maintenance).save(
				new BulkMigrationMaintenanceState(fingerprint,
						BulkMigrationMaintenanceStatus.PREPARED, Instant.EPOCH, null));

		assertThrows(IllegalArgumentException.class,
				() -> migration.recoverMaintenanceWithFingerprint("wrong"));
		assertEquals(0, restores.get());
		final var recovered = migration.recoverMaintenance(reportFile);
		assertTrue(recovered.recovered());
		assertEquals(BulkMigrationMaintenanceStatus.PREPARED,
				recovered.previousState().status());
		assertEquals(BulkMigrationMaintenanceStatus.RESTORED,
				recovered.currentState().status());
		assertEquals(1, restores.get());
		assertFalse(migration.recoverMaintenanceWithFingerprint(fingerprint).recovered());
		assertEquals(1, restores.get());
	}

	@Test
	void postExecutionFailureReportRetainsDurableMaintenanceState()
			throws Exception {
		final JDBCDataSource source = dataSource("facade_report_maintenance_source");
		final JDBCDataSource target = dataSource("facade_report_maintenance_target");
		for (final JDBCDataSource dataSource : List.of(source, target)) {
			try (var connection = dataSource.getConnection();
					var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE ITEMS (ID INTEGER NOT NULL PRIMARY KEY)");
			}
		}
		final Schema schema = new Schema("PUBLIC");
		final Table table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		table.setPrimaryKey("PK_ITEMS", table.getColumns().get("ID"));
		schema.getTables().add(table);
		final BulkMigrationJobLifecycle lifecycle = new BulkMigrationJobLifecycle() {
			@Override
			public String getConfigurationFingerprint() {
				return "post-verification-mismatch-v1";
			}

			@Override
			public void after(final Connection connection,
					final BulkMigrationJobPlan plan,
					final BulkMigrationJobResult result) throws SQLException {
				try (var statement = connection.createStatement()) {
					statement.execute("INSERT INTO ITEMS VALUES (99)");
				}
			}
		};
		final Path reportFile = directory.resolve("post-failure.json");
		final BulkMigration migration = BulkMigration.builder().source(source).target(target)
				.schema(schema).mode(BulkMigrationMode.INSERT).lifecycle(lifecycle)
				.fileMaintenance(directory.resolve("post-failure-maintenance"))
				.operationalReport(reportFile).build();

		assertThrows(BulkMigrationVerificationMismatchException.class, migration::run);
		final var report = new BulkMigrationOperationalReportIO().read(reportFile);
		assertEquals("JOB_FAILED", report.execution().event());
		assertEquals(BulkMigrationMaintenanceStatus.COMPLETE.name(),
				report.maintenance().status());
	}

	@Test
	void operationalReportFailsBeforeExecutionWhenMaintenanceStateIsCorrupt()
			throws Exception {
		final JDBCDataSource source = dataSource("facade_corrupt_maintenance_source");
		final JDBCDataSource target = dataSource("facade_corrupt_maintenance_target");
		for (final JDBCDataSource dataSource : List.of(source, target)) {
			try (var connection = dataSource.getConnection();
					var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE ITEMS (ID INTEGER NOT NULL PRIMARY KEY)");
			}
		}
		final Schema schema = new Schema("PUBLIC");
		final Table table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		table.setPrimaryKey("PK_ITEMS", table.getColumns().get("ID"));
		schema.getTables().add(table);
		final Path maintenance = directory.resolve("corrupt-maintenance");
		final BulkMigration migration = BulkMigration.builder().source(source).target(target)
				.schema(schema).mode(BulkMigrationMode.INSERT)
				.fileMaintenance(maintenance)
				.operationalReport(directory.resolve("corrupt-report.json")).build();
		final String fingerprint = migration.dryRun().planFingerprint();
		new FileBulkMigrationMaintenanceStateStore(maintenance).save(
				new BulkMigrationMaintenanceState(fingerprint,
						BulkMigrationMaintenanceStatus.PREPARED, Instant.EPOCH, null));
		final Path stateFile;
		try (var files = Files.list(maintenance)) {
			stateFile = files.findFirst().orElseThrow();
		}
		Files.writeString(stateFile, "status=UNKNOWN\n", java.nio.charset.StandardCharsets.UTF_8);

		final RuntimeException failure = assertThrows(RuntimeException.class,
				migration::execute);
		assertTrue(failure.getMessage().contains("maintenance state"));
		try (var connection = target.getConnection();
				var result = connection.createStatement()
						.executeQuery("SELECT COUNT(*) FROM ITEMS")) {
			result.next();
			assertEquals(0, result.getInt(1));
		}
	}

	@Test
	void verificationScopeRestoresBothConnections() throws Exception {
		try (Connection source = dataSource("verification_scope_source").getConnection();
				Connection target = dataSource("verification_scope_target").getConnection()) {
			final int sourceIsolation = source.getTransactionIsolation();
			final int targetIsolation = target.getTransactionIsolation();
			try (var ignored = BulkMigrationVerificationScope.open(
					BulkMigrationVerificationIsolation.SERIALIZABLE, source, target)) {
				assertFalse(source.getAutoCommit());
				assertFalse(target.getAutoCommit());
				assertEquals(Connection.TRANSACTION_SERIALIZABLE,
						source.getTransactionIsolation());
				assertEquals(Connection.TRANSACTION_SERIALIZABLE,
						target.getTransactionIsolation());
			}
			assertTrue(source.getAutoCommit());
			assertTrue(target.getAutoCommit());
			assertEquals(sourceIsolation, source.getTransactionIsolation());
			assertEquals(targetIsolation, target.getTransactionIsolation());
		}
	}

	private static JDBCDataSource dataSource(final String name) {
		final JDBCDataSource dataSource = new JDBCDataSource();
		dataSource.setUrl("jdbc:hsqldb:mem:" + name);
		dataSource.setUser("SA");
		return dataSource;
	}

	private static boolean tableExists(final Connection connection, final String name)
			throws SQLException {
		try (var tables = connection.getMetaData().getTables(connection.getCatalog(),
				null, "%", new String[] { "TABLE" })) {
			while (tables.next()) {
				if (name.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
					return true;
				}
			}
		}
		return false;
	}
}
