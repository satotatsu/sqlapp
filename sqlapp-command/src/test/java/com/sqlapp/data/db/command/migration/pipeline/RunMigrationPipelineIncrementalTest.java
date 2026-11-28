/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.migration.pipeline.MigrationPipelineReport.Status;
import com.sqlapp.data.db.command.migration.pipeline.MigrationPipelineReport.Step;
import com.sqlapp.data.db.command.migration.transfer.TransferSchemaPreparation;
import com.sqlapp.data.db.command.migration.transfer.TransferStagedReplacement;
import com.sqlapp.util.JsonConverter;
import com.zaxxer.hikari.HikariDataSource;

/**
 * An incremental model published with STAGED_REPLACE: rows merged in a run
 * whose publish failed are published by the next run, even when it merges
 * nothing.
 */
class RunMigrationPipelineIncrementalTest {
	@TempDir
	Path directory;

	private HikariDataSource engine;
	private JDBCDataSource target;

	@BeforeEach
	void setUp() throws Exception {
		engine = new HikariDataSource();
		engine.setJdbcUrl("jdbc:duckdb:" + directory.resolve("work.duckdb").toString().replace('\\', '/'));
		engine.setMaximumPoolSize(2);
		target = new JDBCDataSource();
		target.setUrl("jdbc:hsqldb:mem:incremental_" + UUID.randomUUID());
		target.setUser("SA");
		target.setPassword("");
		execute(engine, "CREATE TABLE ORDERS(ID INTEGER, AMOUNT DECIMAL(10,2), VERSION INTEGER)",
				"INSERT INTO ORDERS VALUES (1, 100, 1), (2, 200, 1)");
		write("models.yaml", """
				sources:
				  - name: orders
				    table: ORDERS
				models:
				  - name: ORDER_FACT
				    materialized: incremental
				    columns:
				      - {name: ID, type: INT}
				      - {name: AMOUNT, type: "DECIMAL(10,2)"}
				      - {name: VERSION, type: INT}
				    primaryKey: [ID]
				    query: |
				      SELECT ID, AMOUNT, VERSION FROM /*$source('orders')*/orders
				      /*if isIncremental*/
				      WHERE VERSION > (SELECT max(VERSION) FROM /*$ref('ORDER_FACT')*/ORDER_FACT)
				      /*end*/
				""");
		write("publish.yaml", """
				formatVersion: 1
				source: work
				target: dst
				tables: [ORDER_FACT]
				schemaMode: CREATE_MISSING
				targetPreparation: STAGED_REPLACE
				""");
		write("pipeline.yaml", """
				formatVersion: 1
				models: models.yaml
				publish: publish.yaml
				""");
	}

	@AfterEach
	void tearDown() throws Exception {
		engine.close();
		execute(target, "SHUTDOWN");
	}

	@Test
	void rowsMergedBeforeAFailedPublishArePublishedByTheNextRun() throws Exception {
		final String fingerprint = command(null).validateInputs().fingerprint();
		run(fingerprint);
		assertEquals(List.of("1 | 100.00", "2 | 200.00"),
				rows(target, "SELECT ID, AMOUNT FROM ORDER_FACT ORDER BY ID"));

		// The model merges the change; the target rejects it during publishing.
		execute(engine, "UPDATE ORDERS SET AMOUNT = 5000, VERSION = 2 WHERE ID = 1");
		execute(target, "ALTER TABLE ORDER_FACT ADD CONSTRAINT LIMIT_AMOUNT CHECK (AMOUNT < 1000)");
		final RunMigrationPipelineCommand failed = command(fingerprint);
		assertThrows(RuntimeException.class, failed::run);
		final MigrationPipelineReport report = report(failed);
		assertEquals(Status.SUCCEEDED, report.step(Step.MODELS).status());
		assertEquals(Status.FAILED, report.step(Step.PUBLISH).status());
		assertEquals(List.of("1 | 5000.00 | 2"),
				rows(engine, "SELECT ID, AMOUNT, VERSION FROM ORDER_FACT WHERE ID = 1"));
		assertEquals(List.of("1 | 100.00", "2 | 200.00"),
				rows(target, "SELECT ID, AMOUNT FROM ORDER_FACT ORDER BY ID"));

		// The failed replacement rolled back and kept its staging tables.
		final Path result = Path.of(report.step(Step.PUBLISH).resultFile());
		assertEquals(1, stages().size(), stages().toString());
		refusesToReleaseUnsettledOrForeignStages(result);
		assertEquals(1, stages().size(), stages().toString());

		// Nothing is newer than the merged rows, yet the whole table is published.
		execute(target, "ALTER TABLE ORDER_FACT DROP CONSTRAINT LIMIT_AMOUNT");
		assertEquals(List.of("0"),
				rows(engine, "SELECT count(*) FROM ORDERS WHERE VERSION > (SELECT max(VERSION) FROM ORDER_FACT)"));
		final RunMigrationPipelineCommand retried = run(fingerprint);
		assertEquals(Status.SUCCEEDED, report(retried).status());
		// The settled stages of the failed run were dropped before publishing.
		assertTrue(
				report(retried).step(Step.PUBLISH).detail()
						.contains("released staging of run " + report.runId() + " (ROLLED_BACK): SQLAPP_STG_"),
				report(retried).step(Step.PUBLISH).detail());
		assertEquals(List.of(), stages());
		assertEquals(List.of("1 | 5000.00", "2 | 200.00"),
				rows(target, "SELECT ID, AMOUNT FROM ORDER_FACT ORDER BY ID"));
	}

	private void refusesToReleaseUnsettledOrForeignStages(final Path result) throws Exception {
		final String text = Files.readString(result);
		assertTrue(text.contains("\"ROLLED_BACK\""), "recorded state");
		// An unconfirmed commit keeps its stages for comparison.
		final Path uncertain = copy(result, "uncertain", text.replace("\"ROLLED_BACK\"", "\"COMMIT_UNCERTAIN\""));
		final var refused = assertThrows(TransferSchemaPreparation.ValidationException.class,
				() -> TransferStagedReplacement.releaseSettledStage(uncertain, target));
		assertTrue(refused.getMessage().contains("COMMIT_UNCERTAIN"), refused.getMessage());
		// Names that do not follow from the recorded job are never dropped.
		final String stage = stages().getFirst();
		final Path tampered = copy(result, "tampered", text.replace(stage, "ORDER_FACT"));
		final var e = assertThrows(TransferSchemaPreparation.ValidationException.class,
				() -> TransferStagedReplacement.releaseSettledStage(tampered, target));
		assertTrue(e.getMessage().contains("do not follow"), e.getMessage());
		// Another database is left alone.
		final JDBCDataSource other = new JDBCDataSource();
		other.setUrl("jdbc:hsqldb:mem:other_" + UUID.randomUUID());
		other.setUser("SA");
		other.setPassword("");
		final var release = TransferStagedReplacement.releaseSettledStage(result, other);
		assertFalse(release.recordedTarget());
		assertEquals(List.of(), release.droppedStages());
		execute(other, "SHUTDOWN");
	}

	/** Copies a transfer result folder with a changed result report. */
	private Path copy(final Path result, final String name, final String text) throws Exception {
		final Path folder = Files.createDirectories(directory.resolve(name));
		try (var files = Files.list(result.getParent())) {
			for (final Path file : files.filter(Files::isRegularFile).toList()) {
				Files.copy(file, folder.resolve(file.getFileName()));
			}
		}
		return Files.writeString(folder.resolve(result.getFileName()), text, StandardCharsets.UTF_8);
	}

	private List<String> stages() throws Exception {
		return rows(target, "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME LIKE 'SQLAPP_STG_%'");
	}

	private RunMigrationPipelineCommand run(final String fingerprint) throws Exception {
		final RunMigrationPipelineCommand command = command(fingerprint);
		command.run();
		return command;
	}

	private RunMigrationPipelineCommand command(final String fingerprint) throws Exception {
		Files.createDirectories(directory.resolve("runs"));
		final RunMigrationPipelineCommand command = new RunMigrationPipelineCommand();
		command.setConfigurationFile(directory.resolve("pipeline.yaml").toFile());
		command.setWorkspaceDirectory(directory.resolve("runs").toFile());
		command.setDataSource(engine);
		command.setTargetDataSource(target);
		command.setCloseDataSource(false);
		command.setAllowExecution(fingerprint != null);
		command.setExpectedConfigurationFingerprint(fingerprint);
		return command;
	}

	private static MigrationPipelineReport report(final RunMigrationPipelineCommand command) throws Exception {
		return new JsonConverter().fromJsonString(Files.readString(command.getReportFile()),
				MigrationPipelineReport.class);
	}

	private void write(final String name, final String text) throws Exception {
		Files.writeString(directory.resolve(name), text, StandardCharsets.UTF_8);
	}

	private static void execute(final DataSource dataSource, final String... sql) throws Exception {
		try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
			for (final String text : sql) {
				statement.execute(text);
			}
		}
	}

	private static List<String> rows(final DataSource dataSource, final String sql) throws Exception {
		final List<String> rows = new ArrayList<>();
		try (var connection = dataSource.getConnection();
				var statement = connection.createStatement();
				var resultSet = statement.executeQuery(sql)) {
			final int count = resultSet.getMetaData().getColumnCount();
			while (resultSet.next()) {
				final StringBuilder row = new StringBuilder();
				for (int i = 1; i <= count; i++) {
					row.append(i == 1 ? "" : " | ").append(resultSet.getString(i));
				}
				rows.add(row.toString());
			}
		}
		return rows;
	}
}
