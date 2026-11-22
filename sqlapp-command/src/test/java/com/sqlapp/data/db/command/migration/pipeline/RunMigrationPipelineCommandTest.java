/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.sqlapp.data.db.command.migration.transfer.TransferNameMappingIO;
import com.sqlapp.util.JsonConverter;
import com.zaxxer.hikari.HikariDataSource;

/** HSQLDB source, DuckDB engine and HSQLDB target, all embedded. */
class RunMigrationPipelineCommandTest {
	private static final String MODELS = """
			sources:
			  - name: customer
			    table: CUSTOMER
			  - name: orders
			    table: ORDERS
			models:
			  - name: CUSTOMER_SALES
			    materialized: table
			    columns:
			      - {name: CUSTOMER_ID, type: INT}
			      - {name: NAME, type: VARCHAR(50), notNull: true}
			      - {name: TOTAL, type: "DECIMAL(12,2)"}
			    primaryKey: [CUSTOMER_ID]
			    query: |
			      SELECT c.ID AS CUSTOMER_ID, c.NAME, AMOUNT_EXPRESSION AS TOTAL
			      FROM /*$source('customer')*/customer c
			      JOIN /*$source('orders')*/orders o ON o.CUSTOMER_ID = c.ID
			      GROUP BY c.ID, c.NAME
			unitTests:
			  - name: sums-orders
			    model: CUSTOMER_SALES
			    given:
			      customer:
			        columns: [ID, NAME]
			        rows:
			          - [1, ALICE]
			      orders:
			        sql: SELECT 1 AS CUSTOMER_ID, CAST(1.50 AS DECIMAL(10,2)) AS AMOUNT
			    expect:
			      rows:
			        - [1, ALICE, 'EXPECTED']
			""";

	@TempDir
	Path directory;

	private JDBCDataSource source;
	private HikariDataSource engine;
	private JDBCDataSource target;

	@BeforeEach
	void databases() throws Exception {
		source = hsql();
		target = hsql();
		engine = new HikariDataSource();
		engine.setJdbcUrl("jdbc:duckdb:" + directory.resolve("work.duckdb").toString().replace('\\', '/'));
		engine.setMaximumPoolSize(2);
		execute(source, "CREATE TABLE CUSTOMER(ID INTEGER NOT NULL PRIMARY KEY, NAME VARCHAR(50) NOT NULL)",
				"CREATE TABLE ORDERS(ID INTEGER NOT NULL PRIMARY KEY,"
						+ " CUSTOMER_ID INTEGER NOT NULL REFERENCES CUSTOMER(ID), AMOUNT DECIMAL(10,2))",
				"INSERT INTO CUSTOMER VALUES (1, 'alice'), (2, 'bob')",
				"INSERT INTO ORDERS VALUES (10, 1, 150.25), (11, 1, 49.75), (12, 2, 120.00)");
		write("load.yaml", """
				formatVersion: 1
				source: src
				target: work
				tables: [CUSTOMER, ORDERS]
				schemaMode: CREATE_MISSING
				""");
		write("publish.yaml", """
				formatVersion: 1
				source: work
				target: dst
				tables: [CUSTOMER_SALES]
				schemaMode: CREATE_MISSING
				""");
		models("CAST(sum(o.AMOUNT) AS DECIMAL(12,2))");
		write("pipeline.yaml", """
				formatVersion: 1
				load: load.yaml
				models: models.yaml
				publish: publish.yaml
				""");
	}

	@AfterEach
	void close() throws Exception {
		engine.close();
		execute(source, "SHUTDOWN");
		execute(target, "SHUTDOWN");
	}

	@Test
	void runsLoadModelsAndPublishAfterApproval() throws Exception {
		final RunMigrationPipelineCommand review = command(null);
		review.run();
		assertTrue(review.getConfigurationFingerprint().matches("sha256:[0-9a-f]{64}"));
		assertNull(review.getRunDirectory());
		assertEquals(List.of(), Files.list(directory.resolve("runs")).toList(), "review creates no run");

		final RunMigrationPipelineCommand command = command(review.getConfigurationFingerprint());
		command.run();
		final MigrationPipelineReport report = report(command);
		assertEquals(Status.SUCCEEDED, report.status());
		for (final Step step : Step.values()) {
			assertEquals(Status.SUCCEEDED, report.step(step).status(), step.name());
		}
		assertEquals("unit tests passed: 1, models built: 1", report.step(Step.MODELS).detail());
		assertEquals("phase: COMPLETE, verified: true", report.step(Step.PUBLISH).detail());
		assertTrue(Files.isRegularFile(Path.of(report.step(Step.PUBLISH).resultFile())));
		assertEquals(List.of("1 | alice | 200.00", "2 | bob | 120.00"),
				rows(target, "SELECT * FROM CUSTOMER_SALES ORDER BY CUSTOMER_ID"));
		assertEquals(List.of("NAME | VARCHAR | 50 | NO"),
				rows(target, "SELECT COLUMN_NAME, TYPE_NAME, COLUMN_SIZE,"
						+ " IS_NULLABLE FROM INFORMATION_SCHEMA.SYSTEM_COLUMNS WHERE TABLE_NAME = 'CUSTOMER_SALES'"
						+ " AND COLUMN_NAME = 'NAME'"));
		// The run's declared columns were the contract of the publish transfer.
		final var mapping = new TransferNameMappingIO()
				.read(command.getRunDirectory().resolve("publish-job/model-types.yaml"));
		assertTrue(mapping.configuration().tables().get("CUSTOMER_SALES").declaresContract());
	}

	@Test
	void failedModelStopsBeforePublishing() throws Exception {
		// The unit test passes; the declared DECIMAL(12,2) would round 200 / 7.
		models("CAST(sum(o.AMOUNT) / 7 AS DECIMAL(18,4))", "0.2143");
		final RunMigrationPipelineCommand command = command(fingerprint());
		final RuntimeException e = assertThrows(RuntimeException.class, command::run);
		assertTrue(e.getMessage().contains("stopped at MODELS"), e.getMessage());
		final MigrationPipelineReport report = report(command);
		assertEquals(Status.FAILED, report.status());
		assertEquals(Status.SUCCEEDED, report.step(Step.LOAD).status());
		assertEquals(Status.FAILED, report.step(Step.MODELS).status());
		assertTrue(report.step(Step.MODELS).message().contains("TOTAL"), report.step(Step.MODELS).message());
		assertEquals(Status.NOT_RUN, report.step(Step.PUBLISH).status());
		assertEquals(List.of("0"), rows(target,
				"SELECT count(*) FROM INFORMATION_SCHEMA.TABLES" + " WHERE TABLE_NAME = 'CUSTOMER_SALES'"));
	}

	@Test
	void failedPublishKeepsTheTransferReports() throws Exception {
		execute(target,
				"CREATE TABLE CUSTOMER_SALES(CUSTOMER_ID INTEGER NOT NULL PRIMARY KEY,"
						+ " NAME VARCHAR(50) NOT NULL, TOTAL DECIMAL(12,2))",
				"INSERT INTO CUSTOMER_SALES VALUES (9, 'x', 1)");
		final RunMigrationPipelineCommand command = command(fingerprint());
		assertThrows(RuntimeException.class, command::run);
		final MigrationPipelineReport report = report(command);
		assertEquals(Status.SUCCEEDED, report.step(Step.MODELS).status());
		assertEquals(Status.FAILED, report.step(Step.PUBLISH).status());
		assertTrue(report.step(Step.PUBLISH).message().contains("empty"), report.step(Step.PUBLISH).message());
		assertEquals(List.of("9 | x | 1.00"), rows(target, "SELECT * FROM CUSTOMER_SALES"));
	}

	@Test
	void changedDefinitionsNeedANewApproval() throws Exception {
		final String approved = fingerprint();
		models("CAST(sum(o.AMOUNT) AS DECIMAL(12,2)) + 0");
		final RunMigrationPipelineCommand command = command(approved);
		final var e = assertThrows(IllegalArgumentException.class, command::run);
		assertTrue(e.getMessage().contains("changed"), e.getMessage());
		assertNull(command.getRunDirectory());
	}

	@Test
	void rejectsJobsThatTheRunCannotVouchFor() throws Exception {
		write("publish.yaml", Files.readString(directory.resolve("publish.yaml")) + "mappingFile: other.yaml\n");
		var e = assertThrows(IllegalArgumentException.class, command(null)::validateInputs);
		assertTrue(e.getMessage().contains("mappingFile"), e.getMessage());
		write("publish.yaml", """
				formatVersion: 1
				source: work
				target: dst
				tables: [CUSTOMER_SALES]
				schemaMode: CREATE_MISSING
				verify: false
				""");
		e = assertThrows(IllegalArgumentException.class, command(null)::validateInputs);
		assertTrue(e.getMessage().contains("verify"), e.getMessage());
	}

	private String fingerprint() throws Exception {
		return command(null).validateInputs().fingerprint();
	}

	private RunMigrationPipelineCommand command(final String fingerprint) throws Exception {
		Files.createDirectories(directory.resolve("runs"));
		final RunMigrationPipelineCommand command = new RunMigrationPipelineCommand();
		command.setConfigurationFile(directory.resolve("pipeline.yaml").toFile());
		command.setWorkspaceDirectory(directory.resolve("runs").toFile());
		command.setSourceDataSource(source);
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

	private void models(final String totalExpression) throws Exception {
		models(totalExpression, "1.50");
	}

	private void models(final String totalExpression, final String expected) throws Exception {
		write("models.yaml", MODELS.replace("AMOUNT_EXPRESSION", totalExpression).replace("EXPECTED", expected));
	}

	private void write(final String name, final String text) throws Exception {
		Files.writeString(directory.resolve(name), text, StandardCharsets.UTF_8);
	}

	private static JDBCDataSource hsql() {
		final JDBCDataSource dataSource = new JDBCDataSource();
		dataSource.setUrl("jdbc:hsqldb:mem:pipeline_" + UUID.randomUUID());
		dataSource.setUser("SA");
		dataSource.setPassword("");
		return dataSource;
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
