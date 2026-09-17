/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.bulk.MigrationSnapshotExecutionResult;
import com.sqlapp.jdbc.bulk.BulkMigrationJobLeaseUnavailableException;

class ExecuteMigrationSnapshotCommandTest {
	@TempDir Path directory;

	@Test
	void executesYamlSnapshotEndToEnd() throws Exception {
		final JDBCDataSource source = dataSource("snapshot_command_source");
		final JDBCDataSource target = dataSource("snapshot_command_target");
		try (var connection = source.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE CUSTOMER(ID INTEGER, NAME VARCHAR(20))");
			statement.execute("INSERT INTO CUSTOMER VALUES(1,'same'),(2,'new'),(4,'added')");
		}
		try (var connection = target.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE CUSTOMER_HISTORY(ID INTEGER, NAME VARCHAR(20), VALID_FROM TIMESTAMP NOT NULL, VALID_TO TIMESTAMP, IS_CURRENT BOOLEAN NOT NULL)");
			statement.execute("INSERT INTO CUSTOMER_HISTORY VALUES(1,'same',TIMESTAMP '2026-01-01 00:00:00',NULL,TRUE),(2,'old',TIMESTAMP '2026-01-01 00:00:00',NULL,TRUE),(3,'removed',TIMESTAMP '2026-01-01 00:00:00',NULL,TRUE)");
		}
		final Path yaml = configuration();
		final Path approval = directory.resolve("snapshot-approval.json");
		final var approvalCommand = new GenerateMigrationSnapshotApprovalReportCommand();
		approvalCommand.setConfigurationFile(yaml.toFile());
		approvalCommand.setTargetFile(approval.toFile());
		approvalCommand.run();
		Files.writeString(yaml, Files.readString(yaml) + "approvalReportFile: snapshot-approval.json\n");
		final var command = new ExecuteMigrationSnapshotCommand();
		command.setSourceDataSource(source);
		command.setDataSource(target);
		command.setConfigurationFile(yaml.toFile());
		command.run();
		assertEquals(new MigrationSnapshotExecutionResult(2, 2, 1), command.getResult());
		final var report = new MigrationSnapshotExecutionReportIO().read(directory.resolve("snapshot-result.json"));
		assertEquals("CUSTOMER", report.snapshotId());
		assertTrue(report.configurationFingerprint().matches("sha256:[0-9a-f]{64}"));
		assertEquals(approvalCommand.getReport().generatedAt(), report.approvalGeneratedAt());
		assertEquals("sha256:" + HexFormat.of().formatHex(
				MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(approval))),
				report.approvalArtifactFingerprint());
		assertEquals(report, command.getReport());
		assertFalse(Files.exists(directory.resolve("snapshot-failure.json")));
		assertEquals("PUBLIC.CUSTOMER_HISTORY", report.targetTable());
		assertEquals(2, report.expiredRows());
		assertEquals(2, report.insertedRows());
		assertEquals(1, report.unchangedRows());
		assertTrue(report.executorClassName().endsWith("HsqlSetBasedMigrationSnapshotExecutor"));
		try (var connection = target.getConnection(); var statement = connection.createStatement();
				var rs = statement.executeQuery("SELECT COUNT(*) FROM CUSTOMER_HISTORY WHERE IS_CURRENT")) {
			rs.next();
			assertEquals(3, rs.getInt(1));
		}
		final var verify = new VerifyMigrationSnapshotReportCommand();
		verify.setReportFile(directory.resolve("snapshot-result.json").toFile());
		verify.setApprovalFile(approval.toFile());
		verify.run();
		assertEquals(report, verify.getReport());
		assertEquals(approvalCommand.getReport(), verify.getApproval());

		Files.writeString(approval, System.lineSeparator(), StandardOpenOption.APPEND);
		final var error = assertThrows(CommandException.class, verify::run);
		assertEquals("Migration snapshot approval evidence artifact fingerprint mismatch", error.getMessage());
	}

	@Test
	void propagatesFailureAndRollsBackTheCompleteTargetSnapshot() throws Exception {
		final JDBCDataSource source = dataSource("snapshot_command_failure_source");
		final JDBCDataSource target = dataSource("snapshot_command_failure_target");
		try (var connection = source.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE CUSTOMER(ID INTEGER, NAME VARCHAR(20))");
			statement.execute("INSERT INTO CUSTOMER VALUES(1,'forbidden')");
		}
		try (var connection = target.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE CUSTOMER_HISTORY(ID INTEGER, "
					+ "NAME VARCHAR(20) CHECK (NAME <> 'forbidden'), VALID_FROM TIMESTAMP NOT NULL, "
					+ "VALID_TO TIMESTAMP, IS_CURRENT BOOLEAN NOT NULL)");
			statement.execute("INSERT INTO CUSTOMER_HISTORY VALUES"
					+ "(1,'old',TIMESTAMP '2026-01-01 00:00:00',NULL,TRUE)");
		}
		final var command = new ExecuteMigrationSnapshotCommand();
		command.setSourceDataSource(source);
		command.setDataSource(target);
		command.setConfigurationFile(configuration().toFile());

		assertThrows(RuntimeException.class, command::run);
		assertNull(command.getResult());
		assertNull(command.getReport());
		assertFalse(Files.exists(directory.resolve("snapshot-result.json")));
		final var failure = new MigrationSnapshotFailureReportIO().read(directory.resolve("snapshot-failure.json"));
		assertEquals(MigrationSnapshotFailurePhase.DATABASE_EXECUTION, failure.phase());
		assertEquals("CUSTOMER", failure.snapshotId());
		assertTrue(failure.failureType().contains("Exception"));
		try (var connection = target.getConnection(); var statement = connection.createStatement();
				var rs = statement.executeQuery(
						"SELECT NAME, VALID_TO, IS_CURRENT FROM CUSTOMER_HISTORY WHERE ID=1")) {
			assertTrue(rs.next());
			assertEquals("old", rs.getString(1));
			assertNull(rs.getTimestamp(2));
			assertTrue(rs.getBoolean(3));
			assertFalse(rs.next());
		}
	}

	@Test
	void distinguishesACommittedSnapshotFromSuccessReportWriteFailure() throws Exception {
		final JDBCDataSource source = dataSource("snapshot_command_report_failure_source");
		final JDBCDataSource target = dataSource("snapshot_command_report_failure_target");
		try (var connection = source.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE CUSTOMER(ID INTEGER, NAME VARCHAR(20))");
			statement.execute("INSERT INTO CUSTOMER VALUES(1,'new')");
		}
		try (var connection = target.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE CUSTOMER_HISTORY(ID INTEGER, NAME VARCHAR(20), VALID_FROM TIMESTAMP NOT NULL, VALID_TO TIMESTAMP, IS_CURRENT BOOLEAN NOT NULL)");
		}
		final Path yaml = configuration();
		Files.writeString(yaml, Files.readString(yaml).replace("reportFile: snapshot-result.json", "reportFile: ."));
		final var command = new ExecuteMigrationSnapshotCommand();
		command.setSourceDataSource(source);
		command.setDataSource(target);
		command.setConfigurationFile(yaml.toFile());

		assertThrows(CommandException.class, command::run);
		final var failure = new MigrationSnapshotFailureReportIO().read(directory.resolve("snapshot-failure.json"));
		assertEquals(MigrationSnapshotFailurePhase.SUCCESS_REPORT_WRITE, failure.phase());
		try (var connection = target.getConnection(); var statement = connection.createStatement();
				var resultSet = statement.executeQuery("SELECT COUNT(*) FROM CUSTOMER_HISTORY WHERE IS_CURRENT")) {
			resultSet.next();
			assertEquals(1, resultSet.getInt(1));
		}
	}

	@Test
	void rejectsAConcurrentSnapshotBeforeMutatingTheTarget() throws Exception {
		final JDBCDataSource source = dataSource("snapshot_command_lease_source");
		final JDBCDataSource target = dataSource("snapshot_command_lease_target");
		try (var connection = source.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE CUSTOMER(ID INTEGER, NAME VARCHAR(20))");
			statement.execute("INSERT INTO CUSTOMER VALUES(1,'new')");
		}
		try (var connection = target.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE CUSTOMER_HISTORY(ID INTEGER, NAME VARCHAR(20), VALID_FROM TIMESTAMP NOT NULL, VALID_TO TIMESTAMP, IS_CURRENT BOOLEAN NOT NULL)");
			statement.execute("INSERT INTO CUSTOMER_HISTORY VALUES(1,'old',TIMESTAMP '2026-01-01 00:00:00',NULL,TRUE)");
		}
		final Path yaml = configuration();
		final var resolved = new MigrationSnapshotConfigurationResolver().resolve(yaml.toFile());
		final var blocking = BulkMigrationJobLeaseConfiguration.file("blocking-worker", directory.resolve("leases"));
		final var manager = BulkMigrationJobLeaseManagerFactory.create(null, blocking);
		try (var ignored = manager.acquire(resolved.definition().id(), resolved.configurationFingerprint())) {
			final var command = new ExecuteMigrationSnapshotCommand();
			command.setSourceDataSource(source);
			command.setDataSource(target);
			command.setConfigurationFile(yaml.toFile());

			final var error = assertThrows(RuntimeException.class, command::run);
			assertTrue(error.getCause() instanceof BulkMigrationJobLeaseUnavailableException
					|| error.getMessage().contains("lease"));
		}
		try (var connection = target.getConnection(); var statement = connection.createStatement();
				var rs = statement.executeQuery("SELECT NAME, VALID_TO FROM CUSTOMER_HISTORY")) {
			assertTrue(rs.next());
			assertEquals("old", rs.getString(1));
			assertNull(rs.getTimestamp(2));
			assertFalse(rs.next());
		}
	}

	private Path configuration() throws Exception {
		final Schema schema = new Schema("PUBLIC");
		final Table source = new Table("CUSTOMER");
		source.getColumns().add(new Column("ID").setDataType(DataType.INT));
		source.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR));
		final Table target = new Table("CUSTOMER_HISTORY");
		target.getColumns().add(new Column("ID").setDataType(DataType.INT));
		target.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR));
		target.getColumns().add(new Column("VALID_FROM").setDataType(DataType.TIMESTAMP));
		target.getColumns().add(new Column("VALID_TO").setDataType(DataType.TIMESTAMP));
		target.getColumns().add(new Column("IS_CURRENT").setDataType(DataType.BOOLEAN));
		schema.getTables().add(source); schema.getTables().add(target);
		schema.writeXml(directory.resolve("schema.xml").toFile());
		final Path yaml = directory.resolve("snapshot.yaml");
		Files.writeString(yaml, "schemaFile: schema.xml\nsourceTable: CUSTOMER\ntargetTable: CUSTOMER_HISTORY\n"
				+ "keyColumns: [ID]\ntrackedColumns: [NAME]\nvalidFromColumn: VALID_FROM\n"
				+ "validToColumn: VALID_TO\ncurrentColumn: IS_CURRENT\nexpireMissingRows: true\n"
				+ "effectiveAt: 2026-09-16T00:00:00Z\nfetchSize: 2\nbatchSize: 2\n"
				+ "reportFile: snapshot-result.json\nfailureReportFile: snapshot-failure.json\n"
				+ "lease:\n  mode: FILE\n  ownerId: command-test\n  durationSeconds: 60\n"
				+ "  directory: leases\n");
		return yaml;
	}

	private static JDBCDataSource dataSource(final String name) {
		final JDBCDataSource value = new JDBCDataSource();
		value.setUrl("jdbc:hsqldb:mem:" + name);
		value.setUser("SA");
		value.setPassword("");
		return value;
	}
}
