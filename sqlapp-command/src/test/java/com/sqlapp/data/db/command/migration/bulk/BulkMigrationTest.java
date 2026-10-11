/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

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
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.migration.verification.MigrationCutoverReport;
import com.sqlapp.data.db.command.migration.verification.MigrationCutoverEvidenceIO;
import com.sqlapp.data.db.command.migration.verification.MigrationCutoverReportIO;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.MigrationFreshnessCheck;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTaskState;
import com.sqlapp.jdbc.bulk.BulkMigrationJobLifecycle;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlan;
import com.sqlapp.jdbc.bulk.BulkMigrationJobResult;
import com.sqlapp.jdbc.bulk.BulkMigrationJobVerificationResult;
import com.sqlapp.jdbc.bulk.BulkMigrationMode;
import com.sqlapp.jdbc.bulk.BulkMigrationMaintenanceState;
import com.sqlapp.jdbc.bulk.BulkMigrationMaintenanceStatus;
import com.sqlapp.jdbc.bulk.InMemoryBulkMigrationCheckpointStore;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;

class BulkMigrationTest {
	@TempDir
	Path directory;

	@Test
	void combinedExecutionRequiresResultsFromTheSamePlan() {
		final var verification = new BulkMigrationJobVerificationResult("plan-b", List.of());
		assertThrows(IllegalArgumentException.class,
				() -> new BulkMigration.Execution(new BulkMigrationJobResult("plan-a", List.of()), verification));
		assertThrows(IllegalArgumentException.class,
				() -> new BulkMigration.Execution(new BulkMigrationJobResult(List.of()), verification));
	}

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
		final BulkMigration migration = BulkMigration.builder().source(source).target(target).schema(schema)
				.tables("ITEMS").chunkSize(1).verificationReport(directory.resolve("verification/mismatch.json"))
				.build();

		final BulkMigrationOperationalReport dryRun = migration.dryRun();
		assertEquals(List.of("PUBLIC.ITEMS"),
				dryRun.tasks().stream().map(BulkMigrationOperationalReport.Task::taskId).toList());
		assertEquals(BulkMigrationJobTaskState.NOT_STARTED, dryRun.tasks().get(0).state());
		assertEquals(BulkMigrationJobTaskState.NOT_STARTED, migration.status().getTasks().get(0).getState());
		final Path statusFile = directory.resolve("status/initial.json");
		final var statusReport = migration.dryRun(statusFile);
		assertEquals(BulkMigrationJobTaskState.NOT_STARTED, statusReport.tasks().get(0).state());
		assertEquals(statusReport, new BulkMigrationOperationalReportIO().read(statusFile));
		assertEquals("sha256:" + com.sqlapp.util.MessageDigests.SHA256.checksumAsString(statusFile.toFile()),
				migration.getOperationalReportFingerprint());
		final Path manifestFile = directory.resolve("state/manifest.json");
		migration.writeNodeManifest(manifestFile);
		final String manifestFingerprint = "sha256:"
				+ com.sqlapp.util.MessageDigests.SHA256.checksumAsString(manifestFile.toFile());
		assertEquals(manifestFingerprint, migration.getNodeManifestFingerprint());
		assertThrows(CommandException.class, () -> migration.executeModified(manifestFile, manifestFingerprint, 1L));
		assertThrows(IllegalArgumentException.class,
				() -> migration.executeModified(manifestFile, "sha256:" + "0".repeat(64), 1_000_000L));
		assertThrows(IllegalArgumentException.class,
				() -> migration.resetCheckpointsWithFingerprint("wrong-fingerprint"));
		try (var connection = target.getConnection();
				var tables = connection.getMetaData().getTables(connection.getCatalog(), null,
						"SQLAPP_BULK_MIGRATION_CHECKPOINT", new String[] { "TABLE" })) {
			assertFalse(tables.next());
		}
		final Path nextManifestFile = directory.resolve("state/next-manifest.json");
		final var unchanged = migration.executeModifiedAndWriteManifest(manifestFile, nextManifestFile,
				manifestFingerprint, 1_000_000L);
		assertTrue(unchanged.selection().selected().isEmpty());
		assertNull(unchanged.migration());
		assertEquals(manifestFingerprint, migration.getApprovedNodeManifestFingerprint());
		assertEquals(unchanged.current(),
				new com.sqlapp.data.db.command.migration.verification.MigrationNodeManifestIO().read(nextManifestFile));
		assertEquals("sha256:" + com.sqlapp.util.MessageDigests.SHA256.checksumAsString(nextManifestFile.toFile()),
				migration.getNodeManifestFingerprint());
		for (final JDBCDataSource dataSource : List.of(source, target)) {
			try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE CUTOVER_EVENTS(UPDATED_AT TIMESTAMP)");
			}
			try (var connection = dataSource.getConnection();
					var statement = connection.prepareStatement("INSERT INTO CUTOVER_EVENTS VALUES (?)")) {
				statement.setTimestamp(1, Timestamp.from(Instant.parse("2026-10-07T00:00:00Z")));
				statement.executeUpdate();
			}
		}
		final Path cutoverFile = directory.resolve("cutover/decision.json");
		final var freshness = new MigrationFreshnessCheck("clock", null, "PUBLIC", "CUTOVER_EVENTS", "UPDATED_AT", null,
				null, Map.of(), Duration.ofMinutes(1));
		final var cutover = migration.assessCutover(List.of(freshness), Instant.now(), Duration.ofMinutes(1),
				cutoverFile, 1_000_000L);
		assertEquals(com.sqlapp.data.db.command.migration.verification.MigrationCutoverReport.Status.READY,
				cutover.status());
		assertEquals("sha256:" + com.sqlapp.util.MessageDigests.SHA256.checksumAsString(cutoverFile.toFile()),
				migration.getCutoverReportFingerprint());
		final String cutoverFingerprint = migration.getCutoverReportFingerprint();
		assertThrows(IllegalArgumentException.class, () -> migration.approveCutover(cutoverFile,
				"sha256:" + "0".repeat(64), Duration.ofMinutes(1), 1_000_000L));
		assertThrows(CommandException.class,
				() -> migration.approveCutover(cutoverFile, cutoverFingerprint, Duration.ofMinutes(1), 1L));
		assertEquals(cutover,
				migration.approveCutover(cutoverFile, cutoverFingerprint, Duration.ofMinutes(1), 1_000_000L));
		assertEquals(cutoverFingerprint, migration.getApprovedCutoverReportFingerprint());
		final Path expiredCutoverFile = directory.resolve("cutover/expired.json");
		final var cutoverIO = new MigrationCutoverReportIO();
		final var expiredSnapshot = cutoverIO.writeSnapshot(expiredCutoverFile,
				new MigrationCutoverReport(Instant.now().minus(Duration.ofHours(2)),
						MigrationCutoverReport.Status.READY, Duration.ZERO, List.of()),
				null);
		assertThrows(IllegalArgumentException.class, () -> migration.approveCutover(expiredCutoverFile,
				expiredSnapshot.fingerprint(), Duration.ofMinutes(1), 1_000_000L));
		final Path rejectedCutoverFile = directory.resolve("cutover/rejected.json");
		final var rejectedSnapshot = cutoverIO.writeSnapshot(
				rejectedCutoverFile, new MigrationCutoverReport(Instant.now(),
						MigrationCutoverReport.Status.NOT_READY_VERIFICATION_STALE, Duration.ofHours(2), List.of()),
				null);
		assertThrows(IllegalStateException.class, () -> migration.approveCutover(rejectedCutoverFile,
				rejectedSnapshot.fingerprint(), Duration.ofMinutes(1), 1_000_000L));
		final var mismatch = assertThrows(BulkMigrationVerificationMismatchException.class, migration::verifyOrThrow);
		final var verification = mismatch.getVerificationResult();

		assertFalse(mismatch.hasMigrationResult());
		assertNull(mismatch.getMigrationResult());
		assertFalse(verification.isMatch());
		assertTrue(Files.isRegularFile(directory.resolve("verification/mismatch.json")));
		assertFalse(
				new BulkMigrationVerificationReportIO().read(directory.resolve("verification/mismatch.json")).match());
		assertEquals(
				"sha256:" + com.sqlapp.util.MessageDigests.SHA256
						.checksumAsString(directory.resolve("verification/mismatch.json").toFile()),
				migration.getVerificationReportFingerprint());
		assertEquals(0, verification.getExpectedRows());
		assertEquals(1, verification.getActualRows());
		assertThrows(IllegalStateException.class,
				() -> migration.assessCutover(List.of(), directory.resolve("verification/mismatch.json"),
						Duration.ofMinutes(5), directory.resolve("cutover/from-mismatch.json")));
		final Path repairFile = directory.resolve("repair.json");
		final var repair = migration.verifyAndWriteRepairPlan(repairFile);
		assertTrue(repair.isRequired());
		assertFalse(repair.getVerificationResult().isMatch());
		final var report = new BulkMigrationJobRepairPlanReportIO().read(repairFile);
		assertEquals("sha256:" + com.sqlapp.util.MessageDigests.SHA256.checksumAsString(repairFile.toFile()),
				repair.getReportFingerprint());
		assertEquals(repair.getReportFingerprint(), migration.getRepairPlanReportFingerprint());
		assertEquals(0, report.estimatedReplayRows());
		assertEquals(1, report.mismatchChunks());
		final String repairFileFingerprint = repair.getReportFingerprint();
		assertThrows(CommandException.class,
				() -> repair.executeApproved(repairFile, repairFileFingerprint, 3_600L, 1L));
		final var result = repair.executeApproved(repairFile, repairFileFingerprint, 3_600L, 1_000_000L);
		assertEquals(repairFileFingerprint, migration.getApprovedRepairPlanReportFingerprint());
		assertEquals(report.planFingerprint(), result.getPlanFingerprint());
		assertEquals(0, result.getReplayedRows());
		assertEquals(List.of(0L), result.getTasks().get(0).getRepairResult().getChunksWithoutExpectedRows());
	}

	@Test
	void migratesVerifiesAndRepairsMappedAccessColumnsThroughTheFacade() throws Exception {
		final JDBCDataSource source = dataSource("facade_mapped_source");
		final JDBCDataSource target = dataSource("facade_mapped_target");
		try (var connection = source.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ACCESS_ITEMS (ACCESS_ID INTEGER NOT NULL PRIMARY KEY, TXT VARCHAR(20))");
			statement.execute("INSERT INTO ACCESS_ITEMS VALUES (1, 'source value')");
		}
		try (var connection = target.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS (ID INTEGER NOT NULL PRIMARY KEY, TXT VARCHAR(20))");
		}
		final Schema schema = new Schema("PUBLIC");
		final Table table = new Table("ACCESS_ITEMS");
		table.getColumns().add(new Column("ACCESS_ID").setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add(new Column("TXT").setDataType(DataType.VARCHAR).setLength(20));
		table.setPrimaryKey("PK_ACCESS_ITEMS", table.getColumns().get("ACCESS_ID"));
		schema.getTables().add(table);
		final var option = BulkMigrationTableOption.builder().targetTable("PUBLIC.ITEMS")
				.columnMappings(Map.of("ACCESS_ID", "ID"))
				.upsertOption(BulkUpsertOption.builder().keyColumn("ACCESS_ID").build()).build();
		final Path executionReportFile = directory.resolve("mapped-execution.json");
		final BulkMigration migration = BulkMigration.builder().source(source).target(target).schema(schema)
				.tables("ACCESS_ITEMS").tableOption("ACCESS_ITEMS", option).resume(false)
				.executionReport(executionReportFile, 1_000_000L).build();

		assertEquals(1, migration.executeAndVerify().migration().getProcessedRows());
		assertEquals(1, migration.getExecutionReport().processedRows());
		assertEquals("PUBLIC.ACCESS_ITEMS", migration.getExecutionReport().tasks().get(0).taskId());
		assertEquals("sha256:" + com.sqlapp.util.MessageDigests.SHA256.checksumAsString(executionReportFile.toFile()),
				migration.getExecutionReportFingerprint());
		try (var connection = target.getConnection(); var statement = connection.createStatement()) {
			statement.executeUpdate("UPDATE ITEMS SET TXT='changed' WHERE ID=1");
		}
		final var repair = migration.verifyAndPlanRepair();
		assertTrue(repair.isRequired());
		final Path repairFile = directory.resolve("mapped-facade-repair.json");
		final var report = repair.writeJson(repairFile);
		assertEquals(Map.of("ACCESS_ID", "ID"), report.tasks().get(0).repairPlan().columnMappings());
		assertEquals(1, repair.executeApproved(repairFile).getReplayedRows());
		final var successfulVerification = migration.verify();
		assertTrue(successfulVerification.isMatch());
		final Path verificationFile = directory.resolve("mapped-verification.json");
		final var verificationIO = new BulkMigrationVerificationReportIO();
		final var verificationSnapshot = verificationIO.writeSnapshot(verificationFile, verificationIO.fromResult(
				successfulVerification.getPlanFingerprint(), BulkMigrationVerificationIsolation.DEFAULT,
				BulkMigrationVerificationReportIO.DEFAULT_MAX_REPORTED_MISMATCHES, successfulVerification, null));
		final Path cutoverFile = directory.resolve("mapped-cutover.json");
		assertThrows(IllegalArgumentException.class, () -> migration.assessCutover(List.of(), verificationFile,
				"sha256:" + "0".repeat(64), Duration.ofMinutes(5), 1_000_000L, cutoverFile, 1_000_000L));
		final var cutover = migration.assessCutover(List.of(), verificationFile, verificationSnapshot.fingerprint(),
				Duration.ofMinutes(5), 1_000_000L, cutoverFile, 1_000_000L);
		assertEquals(MigrationCutoverReport.Status.READY, cutover.status());
		assertEquals(verificationSnapshot.fingerprint(), migration.getApprovedVerificationReportFingerprint());
		assertEquals("sha256:" + com.sqlapp.util.MessageDigests.SHA256.checksumAsString(cutoverFile.toFile()),
				migration.getCutoverReportFingerprint());
		final Path linkedCutoverFile = directory.resolve("mapped-linked-cutover.json");
		final Path evidenceFile = directory.resolve("mapped-cutover-evidence.json");
		final var evidence = migration.assessCutoverAndWriteEvidence(List.of(), verificationFile,
				verificationSnapshot.fingerprint(), Duration.ofMinutes(5), 1_000_000L, linkedCutoverFile, 1_000_000L,
				evidenceFile, 1_000_000L);
		assertEquals(verificationSnapshot.report().planFingerprint(), evidence.planFingerprint());
		assertEquals(verificationSnapshot.fingerprint(), evidence.verificationReportFingerprint());
		assertEquals(MigrationCutoverReport.Status.READY, evidence.status());
		final String evidenceFingerprint = "sha256:"
				+ com.sqlapp.util.MessageDigests.SHA256.checksumAsString(evidenceFile.toFile());
		assertEquals(evidenceFingerprint, migration.getCutoverEvidenceFingerprint());
		assertEquals(evidence, new MigrationCutoverEvidenceIO().read(evidenceFile));
		assertThrows(IllegalArgumentException.class,
				() -> migration.approveCutoverEvidence(evidenceFile, "sha256:" + "0".repeat(64), verificationFile,
						linkedCutoverFile, Duration.ofMinutes(5), 1_000_000L, 1_000_000L, 1_000_000L));
		assertThrows(CommandException.class, () -> migration.approveCutoverEvidence(evidenceFile, evidenceFingerprint,
				verificationFile, linkedCutoverFile, Duration.ofMinutes(5), 1L, 1_000_000L, 1_000_000L));
		assertEquals(evidence, migration.approveCutoverEvidence(evidenceFile, evidenceFingerprint, verificationFile,
				linkedCutoverFile, Duration.ofMinutes(5), 1_000_000L, 1_000_000L, 1_000_000L));
		assertEquals(evidenceFingerprint, migration.getApprovedCutoverEvidenceFingerprint());
		assertEquals(verificationSnapshot.fingerprint(), migration.getApprovedVerificationReportFingerprint());
		final String previousCutoverFingerprint = migration.getCutoverReportFingerprint();
		final String previousEvidenceFingerprint = migration.getCutoverEvidenceFingerprint();
		final Path failedPackage = directory.resolve("release/failed-package");
		assertThrows(CommandException.class, () -> migration.assessCutoverPackage(List.of(), verificationFile,
				verificationSnapshot.fingerprint(), Duration.ofMinutes(5), 1_000_000L, failedPackage, 1_000_000L, 1L));
		assertFalse(Files.exists(failedPackage));
		assertEquals(previousCutoverFingerprint, migration.getCutoverReportFingerprint());
		assertEquals(previousEvidenceFingerprint, migration.getCutoverEvidenceFingerprint());
		assertEquals(verificationSnapshot.fingerprint(), migration.getApprovedVerificationReportFingerprint());
		final Path packageDirectory = directory.resolve("release/cutover-package");
		final var cutoverPackage = migration.assessCutoverPackage(List.of(), verificationFile,
				verificationSnapshot.fingerprint(), Duration.ofMinutes(5), 1_000_000L, packageDirectory, 1_000_000L,
				1_000_000L);
		assertTrue(Files.isRegularFile(packageDirectory.resolve("verification.json")));
		assertTrue(Files.isRegularFile(packageDirectory.resolve("cutover.json")));
		assertTrue(Files.isRegularFile(packageDirectory.resolve("evidence.json")));
		assertEquals(cutoverPackage.evidenceFingerprint(), migration.getCutoverEvidenceFingerprint());
		final var inspection = migration.inspectCutoverPackage(packageDirectory, 1_000_000L, 1_000_000L, 1_000_000L);
		assertEquals(cutoverPackage.evidence(), inspection.evidence());
		assertEquals(cutoverPackage.evidenceFingerprint(), inspection.evidenceFingerprint());
		assertTrue(inspection.verification().match());
		assertEquals(MigrationCutoverReport.Status.READY, inspection.cutover().status());
		final Path extraPackageFile = packageDirectory.resolve("notes.txt");
		Files.writeString(extraPackageFile, "not covered by evidence");
		assertThrows(CommandException.class, () -> migration.approveCutoverPackage(packageDirectory,
				cutoverPackage.evidenceFingerprint(), Duration.ofMinutes(5)));
		Files.delete(extraPackageFile);
		final Path packagedCutover = packageDirectory.resolve("cutover.json");
		final Path savedCutover = directory.resolve("saved-cutover.json");
		Files.move(packagedCutover, savedCutover);
		assertThrows(CommandException.class, () -> migration.approveCutoverPackage(packageDirectory,
				cutoverPackage.evidenceFingerprint(), Duration.ofMinutes(5)));
		Files.move(savedCutover, packagedCutover);
		assertEquals(cutoverPackage.evidence(), migration.approveCutoverPackage(packageDirectory,
				cutoverPackage.evidenceFingerprint(), Duration.ofMinutes(5), 1_000_000L, 1_000_000L, 1_000_000L));
		assertThrows(CommandException.class, () -> migration.assessCutoverPackage(List.of(), verificationFile,
				Duration.ofMinutes(5), packageDirectory));
		Files.writeString(packageDirectory.resolve("verification.json"), "\n", java.nio.file.StandardOpenOption.APPEND);
		assertThrows(IllegalArgumentException.class, () -> migration.approveCutoverPackage(packageDirectory,
				cutoverPackage.evidenceFingerprint(), Duration.ofMinutes(5)));
		new MigrationCutoverReportIO().write(linkedCutoverFile, new MigrationCutoverReport(
				evidence.assessedAt().plusMillis(1), MigrationCutoverReport.Status.READY, Duration.ZERO, List.of()));
		assertThrows(IllegalArgumentException.class,
				() -> migration.approveCutoverEvidence(evidenceFile, evidenceFingerprint, verificationFile,
						linkedCutoverFile, Duration.ofMinutes(5), 1_000_000L, 1_000_000L, 1_000_000L));
	}

	@Test
	void migratesAndRepairsMappedAccessParentChildTablesInDependencyOrder() throws Exception {
		final JDBCDataSource source = dataSource("facade_mapped_relations_source");
		final JDBCDataSource target = dataSource("facade_mapped_relations_target");
		try (var connection = source.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ACCESS_PARENTS (ACCESS_ID INTEGER NOT NULL PRIMARY KEY, TXT VARCHAR(20))");
			statement.execute("CREATE TABLE ACCESS_CHILDREN (ACCESS_ID INTEGER NOT NULL PRIMARY KEY, "
					+ "ACCESS_PARENT_ID INTEGER NOT NULL, TXT VARCHAR(20), "
					+ "FOREIGN KEY (ACCESS_PARENT_ID) REFERENCES ACCESS_PARENTS(ACCESS_ID))");
			statement.execute("INSERT INTO ACCESS_PARENTS VALUES (1, 'parent source')");
			statement.execute("INSERT INTO ACCESS_CHILDREN VALUES (10, 1, 'child source')");
		}
		try (var connection = target.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE PARENTS (ID INTEGER NOT NULL PRIMARY KEY, TXT VARCHAR(20))");
			statement.execute("CREATE TABLE CHILDREN (ID INTEGER NOT NULL PRIMARY KEY, PARENT_ID INTEGER NOT NULL, "
					+ "TXT VARCHAR(20), FOREIGN KEY (PARENT_ID) REFERENCES PARENTS(ID))");
		}
		final Schema schema = new Schema("PUBLIC");
		final Table parent = new Table("ACCESS_PARENTS");
		parent.getColumns().add(new Column("ACCESS_ID").setDataType(DataType.INT).setNotNull(true));
		parent.getColumns().add(new Column("TXT").setDataType(DataType.VARCHAR).setLength(20));
		parent.setPrimaryKey("PK_ACCESS_PARENTS", parent.getColumns().get("ACCESS_ID"));
		final Table child = new Table("ACCESS_CHILDREN");
		child.getColumns().add(new Column("ACCESS_ID").setDataType(DataType.INT).setNotNull(true));
		child.getColumns().add(new Column("ACCESS_PARENT_ID").setDataType(DataType.INT).setNotNull(true));
		child.getColumns().add(new Column("TXT").setDataType(DataType.VARCHAR).setLength(20));
		child.setPrimaryKey("PK_ACCESS_CHILDREN", child.getColumns().get("ACCESS_ID"));
		child.getConstraints().addForeignKeyConstraint("FK_ACCESS_CHILD_PARENT",
				child.getColumns().get("ACCESS_PARENT_ID"), parent.getColumns().get("ACCESS_ID"));
		schema.getTables().add(child);
		schema.getTables().add(parent);
		final BulkMigration migration = BulkMigration.builder().source(source).target(target).schema(schema)
				.tableOption("ACCESS_PARENTS",
						BulkMigrationTableOption.builder().targetTable("PUBLIC.PARENTS")
								.columnMappings(Map.of("ACCESS_ID", "ID")).build())
				.tableOption("ACCESS_CHILDREN",
						BulkMigrationTableOption.builder().targetTable("PUBLIC.CHILDREN")
								.columnMappings(Map.of("ACCESS_ID", "ID", "ACCESS_PARENT_ID", "PARENT_ID")).build())
				.build();

		assertEquals(List.of("PUBLIC.ACCESS_PARENTS", "PUBLIC.ACCESS_CHILDREN"),
				migration.dryRun().tasks().stream().map(BulkMigrationOperationalReport.Task::taskId).toList());
		assertEquals(2, migration.executeAndVerify().migration().getProcessedRows());
		try (var connection = target.getConnection(); var statement = connection.createStatement()) {
			statement.executeUpdate("UPDATE PARENTS SET TXT='parent changed' WHERE ID=1");
			statement.executeUpdate("UPDATE CHILDREN SET TXT='child changed' WHERE ID=10");
		}

		final var repair = migration.verifyAndPlanRepair();
		final Path repairFile = directory.resolve("mapped-relations-repair.json");
		final var report = repair.writeJson(repairFile);
		assertEquals(List.of("PUBLIC.ACCESS_PARENTS", "PUBLIC.ACCESS_CHILDREN"),
				report.tasks().stream().map(BulkMigrationJobRepairPlanReport.Task::taskId).toList());
		assertEquals(2, repair.executeApproved(repairFile).getReplayedRows());
		assertTrue(migration.verify().isMatch());
	}

	@Test
	void keepsResumeExplicitBecauseSchemaIsNotADataFingerprint() {
		final Schema schema = new Schema("PUBLIC");
		final Table table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setNotNull(true));
		table.setPrimaryKey("PK_ITEMS", table.getColumns().get("ID"));
		schema.getTables().add(table);
		assertThrows(NullPointerException.class, () -> BulkMigration.of(null, dataSource("of_target"), schema));
		assertThrows(NullPointerException.class, () -> BulkMigration.of(dataSource("of_source"), null, schema));
		assertThrows(NullPointerException.class,
				() -> BulkMigration.of(dataSource("of_schema_source"), dataSource("of_schema_target"), null));
		assertThrows(IllegalArgumentException.class, () -> BulkMigration.builder().source(dataSource("invalid_source"))
				.target(dataSource("invalid_target")).schema(schema).resume(true).build());
		assertThrows(IllegalArgumentException.class,
				() -> BulkMigration.builder().source(dataSource("invalid_file_source"))
						.target(dataSource("invalid_file_target")).schema(schema).fileCheckpoints(null).build());
		assertThrows(IllegalArgumentException.class,
				() -> BulkMigration.builder().source(dataSource("invalid_custom_source"))
						.target(dataSource("invalid_custom_target")).schema(schema).customCheckpointStore(null)
						.build());
		assertThrows(IllegalArgumentException.class,
				() -> BulkMigration.builder().source(dataSource("invalid_lease_source"))
						.target(dataSource("invalid_lease_target")).schema(schema).fileLease("worker", null));
		assertThrows(NullPointerException.class, () -> BulkMigration.builder().operationalReport(null));
		assertThrows(NullPointerException.class, () -> BulkMigration.builder().verificationReport(null));
		assertThrows(NullPointerException.class, () -> BulkMigration.builder().repairPlanOnMismatch(null));
		assertThrows(IllegalArgumentException.class,
				() -> BulkMigration.builder().source(dataSource("invalid_report_source"))
						.target(dataSource("invalid_report_target")).schema(schema)
						.verificationReport(directory.resolve("verification.json"), 0).build());
		assertThrows(IllegalArgumentException.class,
				() -> BulkMigration.builder().source(dataSource("invalid_maintenance_source"))
						.target(dataSource("invalid_maintenance_target")).schema(schema)
						.databaseMaintenance("bad.table").build());
		assertThrows(IllegalArgumentException.class,
				() -> BulkMigration.builder().source(dataSource("conflicting_maintenance_source"))
						.target(dataSource("conflicting_maintenance_target")).schema(schema)
						.maintenanceDirectory(directory).maintenanceTableName("MAINTENANCE").build());

		final var customStore = new InMemoryBulkMigrationCheckpointStore();
		final BulkMigration custom = BulkMigration.builder().source(dataSource("custom_source"))
				.target(dataSource("custom_target")).schema(schema).customCheckpointStore(customStore).build();
		assertThrows(NullPointerException.class, () -> custom.dryRun(null));
		assertThrows(NullPointerException.class, () -> custom.verifyAndWriteRepairPlan(null));
		assertThrows(IllegalArgumentException.class, () -> custom.resetCheckpointsWithFingerprint(null));
		assertThrows(IllegalArgumentException.class, () -> custom.resetCheckpointsWithFingerprint(" "));
		assertThrows(IllegalStateException.class, () -> custom.recoverMaintenanceWithFingerprint("approved"));
		assertThrows(IllegalArgumentException.class, () -> custom.recoverMaintenanceWithFingerprint(" "));
		assertThrows(NullPointerException.class, () -> custom.recoverMaintenance(null));
		assertThrows(NullPointerException.class, () -> custom.resetCheckpoints((Path) null));
		assertEquals(BulkMigrationJobTaskState.NOT_STARTED,
				assertDoesNotThrow(() -> custom.status()).getTasks().get(0).getState());
	}

	@Test
	void validatesAndOwnsAdvancedTableOverrides() {
		final List<String> keysetColumns = new ArrayList<>(List.of("ID"));
		final List<String> verificationColumns = new ArrayList<>(List.of("ID", "TXT"));
		final BulkMigrationTableOption option = BulkMigrationTableOption.builder().migrationId("items").chunkSize(10)
				.verificationChunkSize(5).keysetColumns(keysetColumns).verificationColumns(verificationColumns).build();

		keysetColumns.clear();
		verificationColumns.clear();
		assertEquals(List.of("ID"), option.getKeysetColumns());
		assertEquals(List.of("ID", "TXT"), option.getVerificationColumns());
		assertThrows(UnsupportedOperationException.class, () -> option.getKeysetColumns().clear());
		assertTrue(BulkMigrationTableOption.defaults().getKeysetColumns().isEmpty());
		assertTrue(BulkMigrationTableOption.defaults().getColumnMappings().isEmpty());
		assertThrows(IllegalArgumentException.class, () -> BulkMigrationTableOption.builder().migrationId(" ").build());
		assertThrows(IllegalArgumentException.class, () -> BulkMigrationTableOption.builder().chunkSize(0).build());
		assertThrows(IllegalArgumentException.class,
				() -> BulkMigrationTableOption.builder().verificationChunkSize(0).build());
		assertThrows(IllegalArgumentException.class,
				() -> BulkMigrationTableOption.builder().keysetColumns(List.of("ID", "ID")).build());
		assertThrows(IllegalArgumentException.class,
				() -> BulkMigrationTableOption.builder().verificationColumns(List.of(" ")).build());
		assertThrows(IllegalArgumentException.class, () -> BulkMigrationTableOption.builder().targetTable(" ").build());
	}

	@Test
	void appliesOnlyTheRequestedAdvancedTableOverrides() throws Exception {
		final JDBCDataSource source = dataSource("facade_override_source");
		final JDBCDataSource target = dataSource("facade_override_target");
		for (final JDBCDataSource dataSource : List.of(source, target)) {
			try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
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
		final BulkMigration migration = BulkMigration.builder().source(source).target(target).schema(schema)
				.tableOption("ITEMS",
						BulkMigrationTableOption.builder().verificationColumns(List.of("ID")).chunkSize(1).build())
				.verificationIsolation(BulkMigrationVerificationIsolation.REPEATABLE_READ).build();

		final var verification = migration.verify();

		assertEquals(migration.dryRun().planFingerprint(), verification.getPlanFingerprint());
		assertEquals(List.of("ID"), verification.getTasks().get(0).getColumns());
		assertTrue(verification.isMatch());
		assertEquals(1, verification.getTasks().get(0).getVerificationResult().getChunkSize());
		assertEquals(2, verification.getTasks().get(0).getVerificationResult().getChunks().size());
		assertThrows(IllegalArgumentException.class, () -> BulkMigration.builder().source(source).target(target)
				.schema(schema).tableOption("MISSING", BulkMigrationTableOption.defaults()).build());
		assertThrows(IllegalArgumentException.class,
				() -> BulkMigration.builder().source(source).target(target).schema(schema)
						.tableOption("ITEMS", BulkMigrationTableOption.builder().verificationChunkSize(0).build())
						.build());
		table.getColumns().add(new Column("CHANGED_AFTER_VERIFICATION"));
		final var repair = migration.planRepair(verification);
		final IllegalArgumentException changedPlan = assertThrows(IllegalArgumentException.class,
				() -> repair.writeJson(directory.resolve("changed-verification-plan.json")));
		assertTrue(changedPlan.getMessage().contains("differs from the current plan"));
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
		final BulkMigration migration = BulkMigration.builder().source(source).target(target).schema(schema)
				.tables("ITEMS").mode(BulkMigrationMode.INSERT).databaseLease("database-worker").build();

		assertEquals(BulkMigrationResumeReadiness.RESUMABLE, migration.resumeReadiness());
		try (var connection = target.getConnection();
				var tables = connection.getMetaData().getTables(connection.getCatalog(), null, "sqlapp_bulk_job_lease",
						new String[] { "TABLE" })) {
			assertFalse(tables.next());
		}
		assertEquals(0, migration.execute().getProcessedRows());
		final String approvedFingerprint = migration.dryRun().planFingerprint();
		assertEquals(List.of("PUBLIC.ITEMS"),
				migration.resetCheckpointsWithFingerprint(approvedFingerprint).getResetTaskIds());
		assertEquals(BulkMigrationJobTaskState.NOT_STARTED, migration.status().getTasks().get(0).getState());
		try (var connection = target.getConnection();
				var tables = connection.getMetaData().getTables(connection.getCatalog(), null, "sqlapp_bulk_job_lease",
						new String[] { "TABLE" })) {
			assertTrue(tables.next());
		}
	}

	@Test
	void invokesAnOptionalLifecycleOnlyDuringExecution() throws Exception {
		final JDBCDataSource source = dataSource("facade_lifecycle_source");
		final JDBCDataSource target = dataSource("facade_lifecycle_target");
		for (final JDBCDataSource dataSource : List.of(source, target)) {
			try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
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
			public void before(final Connection connection, final BulkMigrationJobPlan plan) throws SQLException {
				events.add("before");
			}

			@Override
			public void after(final Connection connection, final BulkMigrationJobPlan plan,
					final BulkMigrationJobResult result) throws SQLException {
				events.add("after");
			}
		};
		final BulkMigration migration = BulkMigration.builder().source(source).target(target).schema(schema)
				.mode(BulkMigrationMode.INSERT).lifecycle(lifecycle).build();

		assertEquals(BulkMigrationJobTaskState.NOT_STARTED, migration.status().getTasks().get(0).getState());
		assertTrue(events.isEmpty());
		migration.execute();
		assertEquals(List.of("before", "after"), events);
	}

	@Test
	void fileMaintenanceIsSimpleDurableAndVisibleInDryRun(
			@org.junit.jupiter.api.io.TempDir final java.nio.file.Path directory) throws Exception {
		final JDBCDataSource source = dataSource("facade_maintenance_source");
		final JDBCDataSource target = dataSource("facade_maintenance_target");
		for (final JDBCDataSource dataSource : List.of(source, target)) {
			try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE ITEMS (ID INTEGER NOT NULL PRIMARY KEY)");
			}
		}
		final Schema schema = new Schema("PUBLIC");
		final Table table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		table.setPrimaryKey("PK_ITEMS", table.getColumns().get("ID"));
		schema.getTables().add(table);
		final BulkMigration migration = BulkMigration.builder().source(source).target(target).schema(schema)
				.jobId("database-maintenance-job").mode(BulkMigrationMode.INSERT).fileMaintenance(directory)
				.operationalReport(directory.resolve("running.json")).build();

		assertEquals(null, migration.dryRun().maintenance());
		assertEquals(0, migration.execute().getProcessedRows());
		final var report = migration.dryRun();
		assertEquals(com.sqlapp.jdbc.bulk.BulkMigrationMaintenanceStatus.COMPLETE, report.maintenance().status());
		assertEquals(BulkMigrationJobTaskState.COMPLETE, migration.status().getTasks().get(0).getState());
		final var published = new BulkMigrationOperationalReportIO().read(directory.resolve("running.json"));
		assertEquals(BulkMigrationMaintenanceStatus.COMPLETE, published.maintenance().status());
		assertEquals(BulkMigrationOperationalReport.ExecutionEvent.JOB_COMPLETED, published.execution().event());
	}

	@Test
	void databaseMaintenanceUsesReadOnlyInspectionAndDedicatedPersistence() throws Exception {
		final JDBCDataSource source = dataSource("facade_database_maintenance_source");
		final JDBCDataSource target = dataSource("facade_database_maintenance_target");
		for (final JDBCDataSource dataSource : List.of(source, target)) {
			try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE ITEMS (ID INTEGER NOT NULL PRIMARY KEY)");
			}
		}
		final Schema schema = new Schema("PUBLIC");
		final Table table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		table.setPrimaryKey("PK_ITEMS", table.getColumns().get("ID"));
		schema.getTables().add(table);
		final BulkMigration migration = BulkMigration.builder().source(source).target(target).schema(schema)
				.jobId("database-maintenance-job").mode(BulkMigrationMode.INSERT)
				.databaseMaintenance("SQLAPP_FACADE_MAINTENANCE").build();

		assertNull(migration.dryRun().maintenance());
		try (var connection = target.getConnection()) {
			assertFalse(tableExists(connection, "SQLAPP_FACADE_MAINTENANCE"));
		}
		assertEquals(0, migration.execute().getProcessedRows());
		assertEquals(com.sqlapp.jdbc.bulk.BulkMigrationMaintenanceStatus.COMPLETE,
				migration.dryRun().maintenance().status());
		try (var connection = target.getConnection()) {
			assertTrue(tableExists(connection, "SQLAPP_FACADE_MAINTENANCE"));
			connection.setAutoCommit(true);
			try (var result = connection.createStatement()
					.executeQuery("SELECT JOB_ID FROM SQLAPP_FACADE_MAINTENANCE")) {
				result.next();
				assertEquals("database-maintenance-job", result.getString(1));
			}
			final var store = new com.sqlapp.jdbc.bulk.JdbcBulkMigrationMaintenanceStateStore(connection,
					"SQLAPP_FACADE_MAINTENANCE");
			store.save(
					new BulkMigrationMaintenanceState("database-maintenance-job", migration.dryRun().planFingerprint(),
							BulkMigrationMaintenanceStatus.PREPARED, Instant.EPOCH, null));
			assertEquals(BulkMigrationMaintenanceStatus.PREPARED,
					store.load("database-maintenance-job").orElseThrow().status());
		}
		assertEquals(BulkMigrationResumeReadiness.RECOVERY_REQUIRED, migration.resumeReadiness());
		assertTrue(assertThrows(IllegalStateException.class, migration::execute).getMessage()
				.contains("explicit recovery"));
		final String approvedFingerprint = migration.dryRun().planFingerprint();
		assertTrue(migration.recoverMaintenanceWithFingerprint(approvedFingerprint).recovered());

		final BulkMigrationJobLifecycle failingLifecycle = new BulkMigrationJobLifecycle() {
			@Override
			public String getConfigurationFingerprint() {
				return "failing-maintenance-v1";
			}

			@Override
			public void before(final Connection connection, final BulkMigrationJobPlan plan) throws SQLException {
				connection.setAutoCommit(false);
				throw new SQLException("preparation failed");
			}
		};
		final BulkMigration failing = BulkMigration.builder().source(source).target(target).schema(schema)
				.mode(BulkMigrationMode.INSERT).lifecycle(failingLifecycle)
				.databaseMaintenance("SQLAPP_FACADE_MAINTENANCE")
				.operationalReport(directory.resolve("database-failed.json")).build();
		assertThrows(SQLException.class, failing::execute);
		assertEquals(com.sqlapp.jdbc.bulk.BulkMigrationMaintenanceStatus.RESTORED,
				failing.dryRun().maintenance().status());
		final var failedReport = new BulkMigrationOperationalReportIO().read(directory.resolve("database-failed.json"));
		assertEquals(BulkMigrationMaintenanceStatus.RESTORED, failedReport.maintenance().status());
		assertEquals(BulkMigrationOperationalReport.ExecutionEvent.JOB_FAILED, failedReport.execution().event());
	}

	@Test
	void recoversOnlyExplicitlyApprovedInterruptedMaintenance() throws Exception {
		final JDBCDataSource source = dataSource("facade_recovery_source");
		final JDBCDataSource target = dataSource("facade_recovery_target");
		for (final JDBCDataSource dataSource : List.of(source, target)) {
			try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
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
			public void restore(final Connection connection, final BulkMigrationJobPlan plan, final Throwable failure) {
				restores.incrementAndGet();
			}
		};
		final Path maintenance = directory.resolve("maintenance");
		final Path executionReport = directory.resolve("rejected-execution.json");
		final BulkMigration migration = BulkMigration.builder().source(source).target(target).schema(schema)
				.jobId("recovery-job").mode(BulkMigrationMode.INSERT).lifecycle(lifecycle).fileMaintenance(maintenance)
				.operationalReport(executionReport).build();
		final Path reportFile = directory.resolve("approved.json");
		final String fingerprint = migration.dryRun(reportFile).planFingerprint();
		new FileBulkMigrationMaintenanceStateStore(maintenance).save(new BulkMigrationMaintenanceState("recovery-job",
				fingerprint, BulkMigrationMaintenanceStatus.PREPARED, Instant.EPOCH, null));

		assertEquals(BulkMigrationResumeReadiness.RECOVERY_REQUIRED, migration.resumeReadiness());
		assertTrue(assertThrows(IllegalStateException.class, migration::execute).getMessage()
				.contains("explicit recovery"));
		final var rejected = new BulkMigrationOperationalReportIO().read(executionReport);
		assertEquals(BulkMigrationOperationalReport.ExecutionEvent.JOB_REJECTED, rejected.execution().event());
		assertEquals(BulkMigrationMaintenanceStatus.PREPARED, rejected.maintenance().status());
		assertThrows(IllegalArgumentException.class, () -> migration.recoverMaintenanceWithFingerprint("wrong"));
		assertEquals(0, restores.get());
		final String reportFingerprint = "sha256:"
				+ com.sqlapp.util.MessageDigests.SHA256.checksumAsString(reportFile.toFile());
		assertThrows(IllegalArgumentException.class,
				() -> migration.recoverMaintenance(reportFile, "sha256:" + "0".repeat(64), 1_000_000L));
		assertEquals(0, restores.get());
		final var recovered = migration.recoverMaintenance(reportFile, reportFingerprint, 1_000_000L);
		assertEquals(reportFingerprint, migration.getApprovedOperationalReportFingerprint());
		assertTrue(recovered.recovered());
		assertEquals(BulkMigrationMaintenanceStatus.PREPARED, recovered.previousState().status());
		assertEquals(BulkMigrationMaintenanceStatus.RESTORED, recovered.currentState().status());
		assertEquals(1, restores.get());
		assertEquals(BulkMigrationResumeReadiness.RESUMABLE, migration.resumeReadiness());
		assertFalse(migration.recoverMaintenanceWithFingerprint(fingerprint).recovered());
		assertEquals(1, restores.get());
	}

	@Test
	void postExecutionFailureReportRetainsDurableMaintenanceState() throws Exception {
		final JDBCDataSource source = dataSource("facade_report_maintenance_source");
		final JDBCDataSource target = dataSource("facade_report_maintenance_target");
		for (final JDBCDataSource dataSource : List.of(source, target)) {
			try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
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
			public void after(final Connection connection, final BulkMigrationJobPlan plan,
					final BulkMigrationJobResult result) throws SQLException {
				try (var statement = connection.createStatement()) {
					statement.execute("INSERT INTO ITEMS VALUES (99)");
				}
			}
		};
		final Path reportFile = directory.resolve("post-failure.json");
		final BulkMigration migration = BulkMigration.builder().source(source).target(target).schema(schema)
				.mode(BulkMigrationMode.INSERT).lifecycle(lifecycle)
				.fileMaintenance(directory.resolve("post-failure-maintenance")).operationalReport(reportFile).build();

		assertThrows(BulkMigrationVerificationMismatchException.class, migration::run);
		final var report = new BulkMigrationOperationalReportIO().read(reportFile);
		assertEquals(BulkMigrationOperationalReport.ExecutionEvent.JOB_FAILED, report.execution().event());
		assertEquals(BulkMigrationMaintenanceStatus.COMPLETE, report.maintenance().status());
	}

	@Test
	void operationalReportFailsBeforeExecutionWhenMaintenanceStateIsCorrupt() throws Exception {
		final JDBCDataSource source = dataSource("facade_corrupt_maintenance_source");
		final JDBCDataSource target = dataSource("facade_corrupt_maintenance_target");
		for (final JDBCDataSource dataSource : List.of(source, target)) {
			try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE ITEMS (ID INTEGER NOT NULL PRIMARY KEY)");
			}
		}
		final Schema schema = new Schema("PUBLIC");
		final Table table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		table.setPrimaryKey("PK_ITEMS", table.getColumns().get("ID"));
		schema.getTables().add(table);
		final Path maintenance = directory.resolve("corrupt-maintenance");
		final BulkMigration migration = BulkMigration.builder().source(source).target(target).schema(schema)
				.jobId("corrupt-maintenance-job").mode(BulkMigrationMode.INSERT).fileMaintenance(maintenance)
				.operationalReport(directory.resolve("corrupt-report.json")).build();
		final String fingerprint = migration.dryRun().planFingerprint();
		new FileBulkMigrationMaintenanceStateStore(maintenance).save(new BulkMigrationMaintenanceState(
				"corrupt-maintenance-job", fingerprint, BulkMigrationMaintenanceStatus.PREPARED, Instant.EPOCH, null));
		final Path stateFile;
		try (var files = Files.list(maintenance)) {
			stateFile = files.findFirst().orElseThrow();
		}
		Files.writeString(stateFile, "status=UNKNOWN\n", java.nio.charset.StandardCharsets.UTF_8);

		final SQLException failure = assertThrows(SQLException.class, migration::execute);
		assertTrue(failure.getMessage().contains("maintenance state"));
		try (var connection = target.getConnection();
				var result = connection.createStatement().executeQuery("SELECT COUNT(*) FROM ITEMS")) {
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
			try (var ignored = BulkMigrationVerificationScope.open(BulkMigrationVerificationIsolation.SERIALIZABLE,
					source, target)) {
				assertFalse(source.getAutoCommit());
				assertFalse(target.getAutoCommit());
				assertEquals(Connection.TRANSACTION_SERIALIZABLE, source.getTransactionIsolation());
				assertEquals(Connection.TRANSACTION_SERIALIZABLE, target.getTransactionIsolation());
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

	private static boolean tableExists(final Connection connection, final String name) throws SQLException {
		try (var tables = connection.getMetaData().getTables(connection.getCatalog(), null, "%",
				new String[] { "TABLE" })) {
			while (tables.next()) {
				if (name.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
					return true;
				}
			}
		}
		return false;
	}
}
