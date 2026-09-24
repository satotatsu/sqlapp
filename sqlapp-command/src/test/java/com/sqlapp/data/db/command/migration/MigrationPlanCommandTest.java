/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.migration.MigrationValidationResult.State;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.SchemaCompatibility;

class MigrationPlanCommandTest {
	@TempDir
	Path directory;
	private Path up;
	private final JDBCDataSource dataSource = new JDBCDataSource();

	@BeforeEach
	void initialize() throws Exception {
		up = Files.createDirectory(directory.resolve("up"));
		dataSource.setUrl("jdbc:hsqldb:mem:plan_" + UUID.randomUUID());
		dataSource.setUser("SA");
		dataSource.setPassword("");
	}

	private <T extends MigrationCommand> T configure(final T command) {
		command.setDataSource(dataSource);
		command.setCloseDataSource(false);
		command.setSqlDirectory(up.toFile());
		return command;
	}

	private long count(final String query) throws Exception {
		try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
				var rows = statement.executeQuery(query)) {
			assertTrue(rows.next());
			return rows.getLong(1);
		}
	}

	@Test
	void freshDatabasePlanDoesNotCreateHistoryOrExecuteScripts() throws Exception {
		Files.writeString(up.resolve("1_create.sql"), "CREATE TABLE sample(id INT);");
		final var setup = Files.createDirectory(directory.resolve("setup"));
		Files.writeString(setup.resolve("setup.sql"), "CREATE TABLE setup_marker(id INT);");
		final var finalizeDirectory = Files.createDirectory(directory.resolve("finalize"));
		Files.writeString(finalizeDirectory.resolve("finish.sql"), "CREATE TABLE finish_marker(id INT);");
		final var command = configure(new MigrationPlanCommand());
		command.setSetupSqlDirectory(setup.toFile());
		command.setFinalizeSqlDirectory(finalizeDirectory.toFile());
		command.run();
		final var plan = command.getPlan();
		assertFalse(plan.historyExists());
		assertNull(plan.currentVersion());
		assertEquals(1, plan.setupStatements());
		assertEquals(1, plan.finalizeStatements());
		assertEquals(1, plan.pending().size());
		assertEquals(1, plan.pending().get(0).statements());
		assertTrue(plan.pending().get(0).transactional());
		assertFalse(plan.pending().get(0).checksumWillBeRecorded());
		assertFalse(plan.pending().get(0).rollbackAvailable());
		assertFalse(plan.hasBlockers());
		final var strict = configure(new MigrationPlanCommand());
		strict.setRequireDownMigration(true);
		strict.run();
		assertTrue(strict.getPlan().downMigrationRequired());
		assertTrue(strict.getPlan().hasBlockers());
		assertEquals(0, count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC'"));
	}

	@Test
	void writesVersionedJsonArtifactWhenRequested() throws Exception {
		Files.writeString(up.resolve("1_create.sql"), "CREATE TABLE sample(id INT);");
		final Path output = directory.resolve("reports/migration-plan.json");
		final var command = configure(new MigrationPlanCommand());
		command.setOutputFile(output.toFile());
		command.run();
		final MigrationPlanArtifact artifact = new MigrationPlanIO().read(output);
		assertEquals(MigrationPlanArtifact.CURRENT_FORMAT_VERSION, artifact.formatVersion());
		assertEquals(command.getPlan(), artifact.plan());
		assertTrue(Files.readString(output).contains("\"formatVersion\" : 8"));
		assertTrue(artifact.createdAtEpochMillis() > 0);
		assertTrue(artifact.planFingerprint().matches("sha256:[0-9a-f]{64}"));
		final Path tampered = directory.resolve("reports/tampered-plan.json");
		Files.writeString(tampered, Files.readString(output).replace(
				artifact.plan().pending().get(0).sourceChecksum(),
				"sha256:0000000000000000000000000000000000000000000000000000000000000000"));
		assertThrows(RuntimeException.class, () -> new MigrationPlanIO().read(tampered));
		assertEquals(0, count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC'"));
	}

	@Test
	void writesSelectedSqlInExecutionOrderWithoutChangingTheDatabase() throws Exception {
		final var setup = Files.createDirectory(directory.resolve("dry-setup"));
		final var finalizeDirectory = Files.createDirectory(directory.resolve("dry-finalize"));
		Files.writeString(setup.resolve("setup.sql"), "CREATE TABLE setup_marker(id INT);");
		Files.writeString(up.resolve("1_create.sql"), "CREATE TABLE sample(id INT);");
		Files.writeString(finalizeDirectory.resolve("finish.sql"), "CREATE TABLE finish_marker(id INT);");
		final Path dryRun = directory.resolve("reports/migration-plan.sql");
		final var command = configure(new MigrationPlanCommand());
		command.setSetupSqlDirectory(setup.toFile());
		command.setFinalizeSqlDirectory(finalizeDirectory.toFile());
		command.setDryRunOutputFile(dryRun.toFile());
		command.run();
		final String sql = Files.readString(dryRun);
		final int setupAt = sql.indexOf("CREATE TABLE setup_marker");
		final int migrationAt = sql.indexOf("CREATE TABLE sample");
		final int finalizeAt = sql.indexOf("CREATE TABLE finish_marker");
		assertTrue(setupAt >= 0 && setupAt < migrationAt && migrationAt < finalizeAt);
		assertTrue(sql.contains("-- migration 1: 1_create.sql [transactional]"));
		assertEquals(0, count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
				+ "WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_NAME IN ('SETUP_MARKER','SAMPLE','FINISH_MARKER')"));
	}

	@Test
	void expectedPlanCanRequireARecentReview() throws Exception {
		Files.writeString(up.resolve("1_create.sql"), "CREATE TABLE sample(id INT);");
		final Path artifact = directory.resolve("expiring-plan.json");
		final var planner = configure(new MigrationPlanCommand());
		planner.setOutputFile(artifact.toFile());
		planner.run();
		final var migration = configure(new MigrationCommand());
		migration.setExpectedPlanFile(artifact.toFile());
		migration.setExpectedPlanMaxAge(Duration.ofMillis(1));
		Thread.sleep(5L);
		assertThrows(RuntimeException.class, migration::run);
		assertEquals(0, count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
				+ "WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_NAME='SAMPLE'"));
	}

	@Test
	void expectedPlanCanBePinnedToAnExternallyApprovedFingerprint() throws Exception {
		Files.writeString(up.resolve("1_create.sql"), "CREATE TABLE sample(id INT);");
		final Path planFile = directory.resolve("pinned-plan.json");
		final var planner = configure(new MigrationPlanCommand());
		planner.setOutputFile(planFile.toFile());
		planner.run();
		final var artifact = new MigrationPlanIO().read(planFile);
		final var replaced = configure(new MigrationCommand());
		replaced.setExpectedPlanFile(planFile.toFile());
		replaced.setExpectedPlanFingerprint("sha256:" + "0".repeat(64));
		assertThrows(RuntimeException.class, replaced::run);
		assertEquals(0, count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
				+ "WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_NAME='SAMPLE'"));
		final var approved = configure(new MigrationCommand());
		approved.setExpectedPlanFile(planFile.toFile());
		approved.setExpectedPlanFingerprint(artifact.planFingerprint());
		approved.run();
		assertEquals(1, count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
				+ "WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_NAME='SAMPLE'"));
	}

	@Test
	void successfulMigrationWritesAnExecutionAuditReport() throws Exception {
		Files.writeString(up.resolve("1_create.sql"), "CREATE TABLE sample(id INT);");
		final Path reportFile = directory.resolve("reports/migration-execution.json");
		final var migration = configure(new MigrationCommand());
		migration.setExecutionReportFile(reportFile.toFile());
		migration.run();
		final var report = new MigrationExecutionReportIO().read(reportFile);
		assertTrue(report.successful());
		assertTrue(report.executionRequested());
		assertEquals(java.util.List.of(1L), report.selectedVersions());
		assertEquals(java.util.List.of(1L), report.committedVersions());
		assertNull(report.failure());
		assertTrue(report.reportFingerprint().matches("sha256:[0-9a-f]{64}"));
		assertEquals(report, migration.getExecutionReport());
		final Path tampered = directory.resolve("reports/tampered-execution.json");
		Files.writeString(tampered, Files.readString(reportFile).replace("\"successful\" : true",
				"\"successful\" : false"));
		assertThrows(RuntimeException.class, () -> new MigrationExecutionReportIO().read(tampered));
	}

	@Test
	void showVersionOnlyIsNotReportedAsApplied() throws Exception {
		Files.writeString(up.resolve("1_create.sql"), "CREATE TABLE sample(id INT);");
		final Path reportFile = directory.resolve("reports/show-version-execution.json");
		final var migration = configure(new MigrationCommand());
		migration.setShowVersionOnly(true);
		migration.setExecutionReportFile(reportFile.toFile());
		migration.run();
		final var report = new MigrationExecutionReportIO().read(reportFile);
		assertTrue(report.successful());
		assertFalse(report.executionRequested());
		assertEquals(java.util.List.of(1L), report.selectedVersions());
		assertTrue(report.committedVersions().isEmpty());
		final var verifier = new VerifyMigrationExecutionReportCommand();
		verifier.setReportFile(reportFile.toFile());
		verifier.setRequireAllSelectedCommitted(true);
		assertThrows(RuntimeException.class, verifier::run);
	}

	@Test
	void planGuardsRequireAnExpectedPlanFile() {
		final var command = configure(new MigrationCommand());
		command.setExpectedPlanMaxAge(Duration.ofMinutes(5));
		assertThrows(RuntimeException.class, command::run);
	}

	@Test
	void embeddedUndoSatisfiesRequiredRollbackPolicy() throws Exception {
		Files.writeString(up.resolve("1_create.sql"),
				"CREATE TABLE sample(id INT);\n-- //@UNDO\nDROP TABLE sample;");
		final var command = configure(new MigrationPlanCommand());
		command.setRequireDownMigration(true);
		command.run();
		assertTrue(command.getPlan().pending().get(0).rollbackAvailable());
		assertFalse(command.getPlan().hasBlockers());
	}

	@Test
	void expectedPlanDetectsSameStatementCountContentChangeBeforeSetup() throws Exception {
		final Path migrationFile = up.resolve("1_create.sql");
		final String reviewedSql = "CREATE TABLE sample(id INT);";
		Files.writeString(migrationFile, reviewedSql);
		final Path setup = Files.createDirectory(directory.resolve("approval-setup"));
		Files.writeString(setup.resolve("setup.sql"), "CREATE TABLE setup_marker(id INT);");
		final Path artifact = directory.resolve("reviewed-plan.json");
		final var planner = configure(new MigrationPlanCommand());
		planner.setSetupSqlDirectory(setup.toFile());
		planner.setOutputFile(artifact.toFile());
		planner.run();
		assertTrue(planner.getPlan().pending().get(0).sourceChecksum().startsWith("sha256:"));
		Files.writeString(migrationFile, "CREATE TABLE sample(id BIGINT);");
		final var changed = configure(new MigrationCommand());
		changed.setSetupSqlDirectory(setup.toFile());
		changed.setExpectedPlanFile(artifact.toFile());
		assertThrows(RuntimeException.class, changed::run);
		assertEquals(0, count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
				+ "WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_NAME IN ('SAMPLE','SETUP_MARKER')"));
		Files.writeString(migrationFile, reviewedSql);
		final var approved = configure(new MigrationCommand());
		approved.setSetupSqlDirectory(setup.toFile());
		approved.setExpectedPlanFile(artifact.toFile());
		approved.run();
		assertEquals(2, count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
				+ "WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_NAME IN ('SAMPLE','SETUP_MARKER')"));
	}

	@Test
	void expectedPlanCannotBeAppliedToAnotherDatabaseWithTheSameVersion() throws Exception {
		Files.writeString(up.resolve("1_create.sql"), "CREATE TABLE sample(id INT);");
		final Path artifact = directory.resolve("database-bound-plan.json");
		final var planner = configure(new MigrationPlanCommand());
		planner.setOutputFile(artifact.toFile());
		planner.run();
		assertTrue(planner.getPlan().databaseIdentity().connectionFingerprint().startsWith("sha256:"));
		final var other = new JDBCDataSource();
		other.setUrl("jdbc:hsqldb:mem:other_plan_target_" + UUID.randomUUID());
		other.setUser("SA");
		final var migration = new MigrationCommand();
		migration.setDataSource(other);
		migration.setCloseDataSource(false);
		migration.setSqlDirectory(up.toFile());
		migration.setExpectedPlanFile(artifact.toFile());
		assertThrows(RuntimeException.class, migration::run);
		try (var connection = other.getConnection(); var statement = connection.createStatement();
				var rows = statement.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
						+ "WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_NAME='SAMPLE'")) {
			assertTrue(rows.next());
			assertEquals(0, rows.getInt(1));
		}
	}

	@Test
	void planUsesHistoryTargetTransactionModeAndRecordedChecksums() throws Exception {
		Files.writeString(up.resolve("1_create.sql"), "CREATE TABLE sample(id INT);");
		final var migration = configure(new MigrationCommand());
		migration.setChecksumValidation(true);
		migration.run();
		Files.writeString(up.resolve("2_change_NoTran.sql"), "INSERT INTO sample VALUES(2);");
		Files.writeString(up.resolve("3_change.sql"), "INSERT INTO sample VALUES(3);");
		final var command = configure(new MigrationPlanCommand());
		command.setChecksumValidation(true);
		command.setLastChangeToApply(2L);
		command.run();
		final var plan = command.getPlan();
		assertTrue(plan.historyExists());
		assertEquals(1L, plan.currentVersion());
		assertEquals(2L, plan.targetVersion());
		assertEquals(1, plan.pending().size());
		assertEquals(2L, plan.pending().get(0).version());
		assertFalse(plan.pending().get(0).transactional());
		assertTrue(plan.pending().get(0).checksumWillBeRecorded());
		assertEquals(State.VERIFIED, plan.checksumValidation().entries().get(0).state());
		assertFalse(plan.hasBlockers());
		final var strict = configure(new MigrationPlanCommand());
		strict.setLastChangeToApply(2L);
		strict.setRejectNonTransactional(true);
		strict.run();
		assertTrue(strict.getPlan().nonTransactionalRejected());
		assertTrue(strict.getPlan().hasBlockers());
		assertEquals(0, count("SELECT COUNT(*) FROM sample"));
	}

	@Test
	void planReportsChecksumAndHistoryBlockersWithoutMutatingThem() throws Exception {
		Files.writeString(up.resolve("1_create.sql"), "CREATE TABLE sample(id INT);");
		final var migration = configure(new MigrationCommand());
		migration.setChecksumValidation(true);
		migration.run();
		Files.writeString(up.resolve("1_create.sql"), "CREATE TABLE sample(id BIGINT);");
		try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
			statement.execute("INSERT INTO \"changelog\"(\"change_number\",\"status\") VALUES(2,'Errored')");
		}
		final var command = configure(new MigrationPlanCommand());
		command.run();
		final var plan = command.getPlan();
		assertTrue(plan.hasBlockers());
		assertEquals(State.CHANGED, plan.checksumValidation().entries().get(0).state());
		assertEquals(Status.Errored, plan.historyIssues().get(0).status());
		assertEquals("Errored", scalar("SELECT \"status\" FROM \"changelog\" WHERE \"change_number\"=2"));
	}

	@Test
	void nullTargetMatchesMigrationNoOpSemanticsOnAnExistingDatabase() throws Exception {
		Files.writeString(up.resolve("1_create.sql"), "CREATE TABLE sample(id INT);");
		configure(new MigrationCommand()).run();
		Files.writeString(up.resolve("2_change.sql"), "INSERT INTO sample VALUES(2);");
		final var command = configure(new MigrationPlanCommand());
		command.setLastChangeToApply(null);
		command.run();
		assertEquals(1L, command.getPlan().targetVersion());
		assertTrue(command.getPlan().pending().isEmpty());
	}

	@Test
	void reportsOutOfOrderVersionsAndOnlyBlocksWhenStrictModeIsEnabled() throws Exception {
		Files.writeString(up.resolve("1_create.sql"), "CREATE TABLE sample(id INT);");
		Files.writeString(up.resolve("3_change.sql"), "INSERT INTO sample VALUES(3);");
		configure(new MigrationCommand()).run();
		Files.writeString(up.resolve("2_late.sql"), "INSERT INTO sample VALUES(2);");
		final var permissive = configure(new MigrationPlanCommand());
		permissive.run();
		assertEquals(java.util.List.of(2L), permissive.getPlan().outOfOrderVersions());
		assertFalse(permissive.getPlan().outOfOrderRejected());
		assertFalse(permissive.getPlan().hasBlockers());
		final var strict = configure(new MigrationPlanCommand());
		strict.setRejectOutOfOrder(true);
		strict.run();
		assertEquals(java.util.List.of(2L), strict.getPlan().outOfOrderVersions());
		assertTrue(strict.getPlan().outOfOrderRejected());
		assertTrue(strict.getPlan().hasBlockers());
		assertEquals(1, count("SELECT COUNT(*) FROM sample"));
	}

	@Test
	void planReportsBreakingPreMigrationDriftWithoutChangingTheDatabase() throws Exception {
		try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE PRESENT(NAME VARCHAR(20))");
		}
		Files.writeString(up.resolve("1_change.sql"), "INSERT INTO PRESENT VALUES('ok');");
		final Schema expected = new Schema("PUBLIC");
		final Table table = new Table("PRESENT");
		table.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR).setLength(100L));
		expected.getTables().add(table);
		final Path schemaFile = directory.resolve("expected.xml");
		expected.writeXml(schemaFile.toFile());
		final var command = configure(new MigrationPlanCommand());
		command.setPreMigrationSchemaFile(schemaFile.toFile());
		command.run();
		assertEquals(SchemaCompatibility.BREAKING, command.getPlan().schemaDrift().compatibility());
		assertTrue(command.getPlan().hasBlockers());
		assertEquals(0, count("SELECT COUNT(*) FROM PRESENT"));
	}

	@Test
	void failOnBlockersFailsAfterWritingTheReviewArtifact() throws Exception {
		try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE PRESENT(NAME VARCHAR(20))");
		}
		Files.writeString(up.resolve("1_change.sql"), "INSERT INTO PRESENT VALUES('ok');");
		final Schema expected = new Schema("PUBLIC");
		final Table table = new Table("PRESENT");
		table.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR).setLength(100L));
		expected.getTables().add(table);
		final Path schemaFile = directory.resolve("expected-gate.xml");
		expected.writeXml(schemaFile.toFile());
		final Path output = directory.resolve("reports/blocked-plan.json");
		final var command = configure(new MigrationPlanCommand());
		command.setPreMigrationSchemaFile(schemaFile.toFile());
		command.setOutputFile(output.toFile());
		command.setFailOnBlockers(true);
		assertThrows(RuntimeException.class, command::run);
		assertTrue(Files.isRegularFile(output));
		assertTrue(new MigrationPlanIO().read(output).plan().hasBlockers());
		assertEquals(0, count("SELECT COUNT(*) FROM PRESENT"));
	}

	private Object scalar(final String query) throws Exception {
		try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
				var rows = statement.executeQuery(query)) {
			assertTrue(rows.next());
			return rows.getObject(1);
		}
	}
}
