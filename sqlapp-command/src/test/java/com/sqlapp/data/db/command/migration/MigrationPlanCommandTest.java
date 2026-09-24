/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
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
		assertFalse(plan.hasBlockers());
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
		assertTrue(Files.readString(output).contains("\"formatVersion\" : 1"));
		assertEquals(0, count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC'"));
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

	private Object scalar(final String query) throws Exception {
		try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
				var rows = statement.executeQuery(query)) {
			assertTrue(rows.next());
			return rows.getObject(1);
		}
	}
}
