/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.File;
import java.sql.SQLException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.db.command.test.AbstractDbCommandTest;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlanner;
import com.sqlapp.jdbc.bulk.BulkMigrationJobListener;
import com.sqlapp.jdbc.bulk.BulkMigrationJobResult;
import com.sqlapp.jdbc.bulk.BulkMigrationMode;
import com.sqlapp.jdbc.bulk.BulkMigrationJobLeaseMode;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpointMode;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationCheckpointStore;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.YamlConverter;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

class ExecuteBulkMigrationJobCommandTest extends AbstractDbCommandTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void executesProgrammaticPlanAgainstConfiguredDataSource() {
		try (var dataSource = newDataSource()) {
			final var command = new ExecuteBulkMigrationJobCommand();
			command.setDataSource(dataSource);
			command.setCloseDataSource(false);
			command.setPlan(BulkMigrationJobPlanner.plan(List.of()));

			command.run();

			assertNotNull(command.getResult());
			assertEquals(command.getPlan().getFingerprint(),
					command.getResult().getPlanFingerprint());
			assertEquals(List.of(), command.getResult().getTasks());
		}
	}

	@Test
	void executesWithDedicatedDatabaseLeaseConnection() {
		try (var dataSource = newDataSource()) {
			final var command = new ExecuteBulkMigrationJobCommand();
			command.setDataSource(dataSource);
			command.setCloseDataSource(false);
			command.setPlan(BulkMigrationJobPlanner.plan(List.of()));
			command.setLeaseConfiguration(
					BulkMigrationJobLeaseConfiguration.database("gradle-worker"));

			command.run();

			assertNotNull(command.getResult());
			assertEquals(command.getPlan().getFingerprint(),
					command.getResult().getPlanFingerprint());
		}
	}

	@Test
	void executesDeclarativeConfigurationFromSeparateSource() throws Exception {
		try (var source = dataSource("bulk_source");
				var target = dataSource("bulk_target")) {
			executeSql(source, "CREATE TABLE PUBLIC.ITEMS (ID INT NOT NULL PRIMARY KEY, NAME VARCHAR(30))");
			executeSql(source, "INSERT INTO PUBLIC.ITEMS VALUES (1, 'one'), (2, 'two')");
			executeSql(target, "CREATE TABLE PUBLIC.ITEMS (ID INT NOT NULL PRIMARY KEY, NAME VARCHAR(30))");
			executeSql(target, "INSERT INTO PUBLIC.ITEMS VALUES (1, 'one'), (2, 'two')");

			final Schema schema = new Schema("PUBLIC");
			final Table table = new Table("ITEMS");
			table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
			table.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR).setLength(30));
			table.setPrimaryKey("PK_ITEMS", table.getColumns().get("ID"));
			schema.getTables().add(table);
			final File schemaFile = temporaryDirectory.resolve("schema.xml").toFile();
			schema.writeXml(schemaFile);

			final var configuration = new BulkMigrationJobConfiguration();
			configuration.setSchemaFile("schema.xml");
			final var task = new BulkMigrationJobConfiguration.Task();
			task.setId("items");
			task.setTable("PUBLIC.ITEMS");
			task.setResume(false);
			final var bulk = new BulkMigrationJobConfiguration.Bulk();
			bulk.setBatchSize(250);
			bulk.setKeepNulls(true);
			task.setBulk(bulk);
			final var retry = new BulkMigrationJobConfiguration.Retry();
			retry.setMaxRetries(3);
			retry.setSqlStates(List.of("40001"));
			task.setRetry(retry);
			final var lease = new BulkMigrationJobConfiguration.Lease();
			lease.setMode(BulkMigrationJobLeaseMode.DATABASE);
			lease.setOwnerId("yaml-worker");
			lease.setDurationSeconds(90);
			configuration.setLease(lease);
			configuration.setTasks(List.of(task));
			final File configurationFile = temporaryDirectory.resolve("job.yaml").toFile();
			new YamlConverter().writeJsonValue(configurationFile, configuration);
			try (var connection = source.getConnection()) {
				final var resolution = new BulkMigrationJobConfigurationResolver()
						.resolveJob(configurationFile, connection);
				final var plan = resolution.plan();
				assertEquals(List.of("items"), plan.getTaskIds());
				assertEquals(BulkMigrationJobLeaseMode.DATABASE,
						resolution.leaseConfiguration().mode());
				assertEquals("yaml-worker", resolution.leaseConfiguration().ownerId());
				assertEquals(90, resolution.leaseConfiguration().duration().toSeconds());
				final var options = plan.getTasks().get(0).getOptions();
				assertEquals(250, options.getBulkOption().getBatchSize());
				assertEquals(true, options.getBulkOption().isKeepNulls());
				assertSame(options.getBulkOption(),
						options.getBulkUpsertOption().getBulkOption());
				assertEquals(3, options.getRetryOption().getMaxRetries());
				assertEquals(List.of("40001"), options.getRetryOption().getSqlStates());
				try (var targetConnection = target.getConnection()) {
					targetConnection.setAutoCommit(true);
					final var reportPlan = ExecuteBulkMigrationJobCommand
							.withExplicitDatabaseCheckpointStores(plan, targetConnection);
					assertEquals(plan.getFingerprint(), reportPlan.getFingerprint());
					assertEquals(JdbcBulkMigrationCheckpointStore.class,
							reportPlan.getTasks().get(0).getCheckpointStore().getClass());
					final Path reportFile = temporaryDirectory.resolve("reports/non-empty.json");
					new BulkMigrationOperationalReportJobListener(reportPlan, reportFile)
							.onJobStarted(reportPlan.getFingerprint(), 1);
					assertEquals(true, Files.isRegularFile(reportFile));
					final var verificationResult = ExecuteBulkMigrationJobCommand.verify(
							plan, targetConnection, 1);
					assertEquals(true, verificationResult.isMatch());
					assertEquals(2, verificationResult.getExpectedRows());
					assertEquals(List.of("ID", "NAME"),
							verificationResult.getTasks().get(0).getColumns());
					executeSql(targetConnection,
							"UPDATE PUBLIC.ITEMS SET NAME='changed' WHERE ID=2");
					final var mismatch = ExecuteBulkMigrationJobCommand.verify(
							plan, targetConnection, 1);
					assertEquals(false, mismatch.isMatch());
					assertEquals(1, mismatch.getMismatchedTasks());
					final var idOnly = ExecuteBulkMigrationJobCommand.verify(plan,
							targetConnection, 1, Map.of("items", List.of("ID")));
					assertEquals(true, idOnly.isMatch());
					assertEquals(List.of("ID"), idOnly.getTasks().get(0).getColumns());
					final int originalIsolation = targetConnection.getTransactionIsolation();
					final boolean originalAutoCommit = targetConnection.getAutoCommit();
					assertEquals(true, ExecuteBulkMigrationJobCommand.verifyWithIsolation(plan,
							targetConnection, 1, Map.of("items", List.of("ID")),
							BulkMigrationVerificationIsolation.REPEATABLE_READ).isMatch());
					assertEquals(originalIsolation, targetConnection.getTransactionIsolation());
					assertEquals(originalAutoCommit, targetConnection.getAutoCommit());
					final Path idOnlyReport = temporaryDirectory.resolve("reports/id-only.json");
					new BulkMigrationVerificationReportIO().write(idOnlyReport,
							plan.getFingerprint(), idOnly);
					final var idOnlyArtifact = new BulkMigrationVerificationReportIO()
							.read(idOnlyReport, plan.getFingerprint());
					assertEquals(List.of("ID"), idOnlyArtifact.tasks().get(0).columns());
					assertEquals("DEFAULT", idOnlyArtifact.isolation());
				}
			}

			task.setCheckpointMode(BulkMigrationCheckpointMode.FILE);
			task.setCheckpointDirectory("checkpoints");
			task.setRetry(new BulkMigrationJobConfiguration.Retry());
			new YamlConverter().writeJsonValue(configurationFile, configuration);
			try (var connection = source.getConnection()) {
				final var filePlan = new BulkMigrationJobConfigurationResolver()
						.resolve(configurationFile, connection);
				assertEquals(FileBulkMigrationCheckpointStore.class,
						filePlan.getTasks().get(0).getCheckpointStore().getClass());
			}

			final var mismatchReport = new BulkMigrationJobConfiguration.Report();
			mismatchReport.setTargetFile("reports/mismatch-status.json");
			configuration.setReport(mismatchReport);
			final var mismatchVerification = new BulkMigrationJobConfiguration.Verification();
			mismatchVerification.setTargetFile("reports/mismatch-verification.json");
			mismatchVerification.setIsolation(
					BulkMigrationVerificationIsolation.REPEATABLE_READ);
			configuration.setVerification(mismatchVerification);
			configuration.setLease(null);
			task.setMode(BulkMigrationMode.INSERT);
			executeSql(source, "TRUNCATE TABLE PUBLIC.ITEMS");
			executeSql(target, "TRUNCATE TABLE PUBLIC.ITEMS");
			new YamlConverter().writeJsonValue(configurationFile, configuration);
			final var mismatchCommand = new ExecuteBulkMigrationJobCommand();
			mismatchCommand.setDataSource(target);
			mismatchCommand.setSourceDataSource(source);
			mismatchCommand.setCloseDataSource(false);
			mismatchCommand.setConfigurationFile(configurationFile);
			mismatchCommand.setListener(new BulkMigrationJobListener() {
				@Override
				public void onJobCompleted(final BulkMigrationJobResult result) {
					try {
						executeSql(target, "INSERT INTO PUBLIC.ITEMS VALUES (3, 'after')");
					} catch (SQLException e) {
						throw new RuntimeException(e);
					}
				}
			});
			assertThrows(RuntimeException.class, mismatchCommand::run);
			assertEquals(false, mismatchCommand.getVerificationResult().isMatch());
			assertEquals("JOB_FAILED", new BulkMigrationOperationalReportIO().read(
					temporaryDirectory.resolve("reports/mismatch-status.json"))
						.execution().event());
			final var mismatchArtifact = new BulkMigrationVerificationReportIO().read(
					temporaryDirectory.resolve("reports/mismatch-verification.json"));
			assertEquals(false, mismatchArtifact.match());
			assertEquals("REPEATABLE_READ", mismatchArtifact.isolation());
			assertNotNull(mismatchArtifact.tasks().get(0).expectedKeysetFingerprint());
			assertNotNull(mismatchArtifact.tasks().get(0).actualKeysetFingerprint());
			final var mismatchChunk = mismatchArtifact.tasks().get(0).mismatches().get(0);
			assertEquals(1, mismatchArtifact.tasks().get(0).mismatchedChunks());
			assertEquals(null, mismatchChunk.expectedFirstKey());
			assertNotNull(mismatchChunk.actualFirstKey());
			assertNotNull(mismatchChunk.actualLastKey());

			final var twoMismatches = new com.sqlapp.jdbc.bulk.BulkMigrationVerificationResult(
					1, 2, 2, List.of(
							new com.sqlapp.jdbc.bulk.BulkMigrationVerificationChunk(
									0, 1, 1, "a", "b"),
							new com.sqlapp.jdbc.bulk.BulkMigrationVerificationChunk(
									1, 1, 1, "c", "d")));
			final var cappedResult = new com.sqlapp.jdbc.bulk.BulkMigrationJobVerificationResult(
					List.of(new com.sqlapp.jdbc.bulk.BulkMigrationJobTaskVerificationResult(
							"items", List.of("ID"), twoMismatches)));
			final Path cappedFile = temporaryDirectory.resolve("reports/capped.json");
			new BulkMigrationVerificationReportIO().write(cappedFile, "plan",
					BulkMigrationVerificationIsolation.DEFAULT, 1, cappedResult);
			final var cappedTask = new BulkMigrationVerificationReportIO().read(cappedFile)
					.tasks().get(0);
			assertEquals(2, cappedTask.mismatchedChunks());
			assertEquals(1, cappedTask.mismatches().size());

			configuration.setTasks(List.of());
			final var report = new BulkMigrationJobConfiguration.Report();
			report.setTargetFile("reports/status.json");
			configuration.setReport(report);
			final var verification = new BulkMigrationJobConfiguration.Verification();
			verification.setChunkSize(1);
			verification.setTargetFile("reports/verification.json");
			configuration.setVerification(verification);
			// Verification is a common post-processing step and must also run when no
			// cross-process lease is configured.
			configuration.setLease(null);
			new YamlConverter().writeJsonValue(configurationFile, configuration);

			final var command = new ExecuteBulkMigrationJobCommand();
			command.setDataSource(target);
			command.setSourceDataSource(source);
			command.setCloseDataSource(false);
			command.setConfigurationFile(configurationFile);
			command.run();

			assertEquals(0, command.getResult().getTasks().size());
			assertEquals(true, command.getVerificationResult().isMatch());
			assertEquals(true, Files.isRegularFile(
					temporaryDirectory.resolve("reports/verification.json")));
			final var verificationReport = new BulkMigrationVerificationReportIO().read(
					temporaryDirectory.resolve("reports/verification.json"),
					command.getResult().getPlanFingerprint());
			assertEquals(true, verificationReport.match());
			assertEquals(0, verificationReport.mismatchedTasks());
			assertThrows(CommandException.class, () ->
					new BulkMigrationVerificationReportIO().read(
							temporaryDirectory.resolve("reports/verification.json"), "wrong"));
			assertEquals(true, Files.isRegularFile(
					temporaryDirectory.resolve("reports/status.json")));

			final var fileLease = new BulkMigrationJobConfiguration.Lease();
			fileLease.setMode(BulkMigrationJobLeaseMode.FILE);
			fileLease.setOwnerId("file-worker");
			fileLease.setDirectory("leases");
			configuration.setLease(fileLease);
			verification.setTargetFile("reports/verification-file-lease.json");
			new YamlConverter().writeJsonValue(configurationFile, configuration);
			final var fileLeaseCommand = new ExecuteBulkMigrationJobCommand();
			fileLeaseCommand.setDataSource(target);
			fileLeaseCommand.setSourceDataSource(source);
			fileLeaseCommand.setCloseDataSource(false);
			fileLeaseCommand.setConfigurationFile(configurationFile);
			fileLeaseCommand.run();
			assertNotNull(fileLeaseCommand.getVerificationResult());
			assertEquals(true, Files.isRegularFile(temporaryDirectory
					.resolve("reports/verification-file-lease.json")));
		}
	}

	@Test
	void rejectsAmbiguousUnqualifiedTableName() throws Exception {
		try (var source = dataSource("bulk_ambiguous")) {
			final Catalog catalog = new Catalog("CATALOG");
			for (final String schemaName : List.of("A", "B")) {
				final Schema schema = new Schema(schemaName);
				final Table table = new Table("ITEMS");
				table.getColumns().add(new Column("ID").setDataType(DataType.INT)
						.setNotNull(true));
				table.setPrimaryKey("PK_" + schemaName, table.getColumns().get("ID"));
				schema.getTables().add(table);
				catalog.getSchemas().add(schema);
			}
			final File schemaFile = temporaryDirectory.resolve("ambiguous.xml").toFile();
			catalog.writeXml(schemaFile);
			final var configuration = new BulkMigrationJobConfiguration();
			configuration.setSchemaFile("ambiguous.xml");
			final var task = new BulkMigrationJobConfiguration.Task();
			task.setId("items");
			task.setTable("ITEMS");
			task.setResume(false);
			configuration.setTasks(List.of(task));
			final File configurationFile = temporaryDirectory.resolve("ambiguous.yaml").toFile();
			new YamlConverter().writeJsonValue(configurationFile, configuration);

			try (var connection = source.getConnection()) {
				assertThrows(CommandException.class,
						() -> new BulkMigrationJobConfigurationResolver()
								.resolve(configurationFile, connection));
			}
		}
	}

	@Test
	void rejectsInvalidVerificationColumnsBeforeExecution() throws Exception {
		try (var source = dataSource("bulk_invalid_verification")) {
			final Schema schema = new Schema("PUBLIC");
			final Table table = new Table("ITEMS");
			table.getColumns().add(new Column("ID").setDataType(DataType.INT));
			table.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR));
			schema.getTables().add(table);
			final File schemaFile = temporaryDirectory.resolve("verification-schema.xml").toFile();
			schema.writeXml(schemaFile);

			final var configuration = new BulkMigrationJobConfiguration();
			configuration.setSchemaFile(schemaFile.getName());
			configuration.setVerification(new BulkMigrationJobConfiguration.Verification());
			final var task = new BulkMigrationJobConfiguration.Task();
			task.setId("items");
			task.setTable("PUBLIC.ITEMS");
			configuration.setTasks(List.of(task));
			final File configurationFile = temporaryDirectory.resolve("verification-job.yaml")
					.toFile();

			try (var connection = source.getConnection()) {
				task.setVerificationColumns(List.of("MISSING"));
				new YamlConverter().writeJsonValue(configurationFile, configuration);
				assertThrows(CommandException.class, () ->
						new BulkMigrationJobConfigurationResolver()
								.resolve(configurationFile, connection));

				task.setVerificationColumns(List.of("ID", "ID"));
				new YamlConverter().writeJsonValue(configurationFile, configuration);
				assertThrows(CommandException.class, () ->
						new BulkMigrationJobConfigurationResolver()
								.resolve(configurationFile, connection));
			}
		}
	}

	private static HikariDataSource dataSource(final String name) {
		final HikariConfig config = new HikariConfig();
		config.setJdbcUrl("jdbc:hsqldb:mem:" + name);
		config.setUsername("SA");
		config.setPassword("");
		return new HikariDataSource(config);
	}
}
