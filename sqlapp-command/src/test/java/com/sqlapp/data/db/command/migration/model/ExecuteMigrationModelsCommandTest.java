/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.migration.model.MigrationModelRunner.Status;
import com.zaxxer.hikari.HikariDataSource;

/** Runs models on an embedded DuckDB database; no external database is used. */
class ExecuteMigrationModelsCommandTest {
	@TempDir
	Path directory;
	private HikariDataSource dataSource;

	private static final String CONFIGURATION = """
			sources:
			  - name: raw_customer
			    file: data/customer.csv
			  - name: raw_order
			    table: main.raw_order
			models:
			  - name: customer_sales
			    sql: models/customer_sales.sql
			    materialized: table
			    parameters:
			      minimumAmount: 100
			  - name: stg_customer
			    query: |
			      SELECT id, upper(name) AS name
			      FROM /*$source('raw_customer')*/raw_customer;
			  - name: stg_order
			    query: SELECT customer_id, amount FROM /*$source('raw_order')*/raw_order
			unitTests:
			  - name: sums-orders-above-minimum
			    model: customer_sales
			    given:
			      stg_customer:
			        columns: [id, name]
			        rows:
			          - [1, ALICE]
			          - [2, BOB]
			      stg_order:
			        sql: SELECT * FROM (VALUES (1, 150), (1, 50), (1, 200), (2, 99)) AS v(customer_id, amount)
			    expect:
			      rows:
			        - [1, ALICE, 350]
			""";

	private static final String CUSTOMER_SALES = """
			-- Sales per customer above the configured minimum.
			WITH big AS (
			  SELECT customer_id, amount FROM /*$ref('stg_order')*/stg_order WHERE amount >= /*minimumAmount*/0
			)
			SELECT c.id, c.name, sum(big.amount) AS total
			FROM /*$ref('stg_customer')*/stg_customer c
			JOIN big ON big.customer_id = c.id
			GROUP BY c.id, c.name
			""";

	@BeforeEach
	void setUp() throws Exception {
		Files.createDirectories(directory.resolve("data"));
		Files.createDirectories(directory.resolve("models"));
		Files.writeString(directory.resolve("data/customer.csv"), "id,name\n1,alice\n2,bob\n", StandardCharsets.UTF_8);
		write("models/customer_sales.sql", CUSTOMER_SALES);
		write("models.yaml", CONFIGURATION);
		dataSource = new HikariDataSource();
		dataSource.setJdbcUrl("jdbc:duckdb:" + directory.resolve("work.duckdb").toString().replace('\\', '/'));
		dataSource.setMaximumPoolSize(2);
		execute("CREATE TABLE raw_order(customer_id INTEGER, amount INTEGER)");
		execute("INSERT INTO raw_order VALUES (1, 150), (1, 50), (1, 200), (2, 99), (2, 120)");
	}

	@AfterEach
	void tearDown() {
		dataSource.close();
	}

	private void write(final String path, final String content) throws Exception {
		Files.writeString(directory.resolve(path), content, StandardCharsets.UTF_8);
	}

	private void execute(final String sql) throws SQLException {
		try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}

	private List<List<String>> query(final String sql) throws SQLException {
		final List<List<String>> rows = new ArrayList<>();
		try (Connection connection = dataSource.getConnection();
				var statement = connection.createStatement();
				var resultSet = statement.executeQuery(sql)) {
			final int count = resultSet.getMetaData().getColumnCount();
			while (resultSet.next()) {
				final List<String> row = new ArrayList<>();
				for (int i = 1; i <= count; i++) {
					row.add(String.valueOf(resultSet.getObject(i)));
				}
				rows.add(row);
			}
		}
		return rows;
	}

	private ExecuteMigrationModelsCommand command() {
		final ExecuteMigrationModelsCommand command = new ExecuteMigrationModelsCommand();
		command.setConfigurationFile(directory.resolve("models.yaml").toFile());
		command.setDataSource(dataSource);
		command.setCloseDataSource(false);
		return command;
	}

	@Test
	void testsAndBuildsModelsInDependencyOrder() throws Exception {
		final ExecuteMigrationModelsCommand command = command();
		command.setStateFile(directory.resolve("state/models.json").toFile());

		command.run();

		assertEquals(1, command.getUnitTestResults().size());
		assertTrue(command.getUnitTestResults().get(0).match());
		assertEquals(List.of("stg_customer", "stg_order", "customer_sales"),
				command.getResult().models().stream().map(MigrationModelRunner.ModelResult::name).toList());
		assertTrue(command.getResult().isSuccess());
		assertEquals(List.of(List.of("1", "ALICE", "350"), List.of("2", "BOB", "120")),
				query("SELECT id, name, total FROM customer_sales ORDER BY id"));
		assertEquals(
				List.of(List.of("customer_sales", "BASE TABLE"), List.of("stg_customer", "VIEW"),
						List.of("stg_order", "VIEW")),
				query("SELECT table_name, table_type FROM information_schema.tables"
						+ " WHERE table_name <> 'raw_order' ORDER BY table_name"));
		assertTrue(Files.isRegularFile(directory.resolve("state/models.json")));
	}

	@Test
	void modifiedOnlyBuildsChangedModelsAndDownstream() throws Exception {
		final ExecuteMigrationModelsCommand first = command();
		first.setStateFile(directory.resolve("models.json").toFile());
		final ExecuteMigrationModelsCommand missingState = command();
		missingState.setStateFile(directory.resolve("models.json").toFile());
		missingState.setModifiedOnly(true);
		assertTrue(assertThrows(RuntimeException.class, missingState::run).getMessage().contains("stateFile"));
		first.run();

		write("models.yaml",
				CONFIGURATION
						.replace("SELECT customer_id, amount FROM", "SELECT customer_id, amount * 2 AS amount FROM")
						.replace("- [1, ALICE, 350]", "- [1, ALICE, 350]\n        - [2, BOB, 198]")
						.replace("(2, 99)) AS v", "(2, 198)) AS v"));
		final ExecuteMigrationModelsCommand second = command();
		second.setStateFile(directory.resolve("models.json").toFile());
		second.setModifiedOnly(true);
		second.run();

		assertEquals(List.of("stg_order", "customer_sales"),
				second.getResult().models().stream().map(MigrationModelRunner.ModelResult::name).toList());
		assertEquals(List.of(List.of("1", "ALICE", "800"), List.of("2", "BOB", "438")),
				query("SELECT id, name, total FROM customer_sales ORDER BY id"));

		final ExecuteMigrationModelsCommand third = command();
		third.setStateFile(directory.resolve("models.json").toFile());
		third.setModifiedOnly(true);
		third.run();
		assertTrue(third.getResult().models().isEmpty());
	}

	@Test
	void failedUnitTestPreventsEveryBuild() throws Exception {
		write("models.yaml", CONFIGURATION.replace("- [1, ALICE, 350]", "- [1, ALICE, 351]"));
		final ExecuteMigrationModelsCommand command = command();
		command.setStateFile(directory.resolve("models.json").toFile());

		final RuntimeException e = assertThrows(RuntimeException.class, command::run);

		assertTrue(e.getMessage().contains("sums-orders-above-minimum"), e.getMessage());
		assertNull(command.getResult());
		assertEquals(List.of(List.of("raw_order")), query("SELECT table_name FROM information_schema.tables"));
		assertFalse(Files.exists(directory.resolve("models.json")));

		final ExecuteMigrationModelsCommand testOnly = command();
		write("models.yaml", CONFIGURATION);
		testOnly.setTestOnly(true);
		testOnly.run();
		assertNull(testOnly.getResult());
		assertEquals(List.of(List.of("raw_order")), query("SELECT table_name FROM information_schema.tables"));
	}

	@Test
	void failedModelRollsBackEveryModelOfTheRun() throws Exception {
		write("models.yaml", CONFIGURATION.replace("SELECT customer_id, amount FROM",
				"SELECT customer_id, missing_column AS amount FROM"));
		final ExecuteMigrationModelsCommand command = command();
		command.setStateFile(directory.resolve("models.json").toFile());
		command.setUnitTestsEnabled(false);

		final RuntimeException e = assertThrows(RuntimeException.class, command::run);

		assertTrue(e.getMessage().contains("stg_order"), e.getMessage());
		assertTrue(e.getMessage().contains("rolled back"), e.getMessage());
		assertEquals(List.of(Status.ROLLED_BACK, Status.FAILED, Status.SKIPPED),
				command.getResult().models().stream().map(MigrationModelRunner.ModelResult::status).toList());
		assertEquals(List.of(List.of("raw_order")),
				query("SELECT table_name FROM information_schema.tables ORDER BY table_name"));
		assertFalse(Files.exists(directory.resolve("models.json")));
	}

	@Test
	void rejectsInvalidConfiguration() throws Exception {
		write("models.yaml", CONFIGURATION.replace("materialized: table", "materialized: incremental"));
		assertTrue(assertThrows(RuntimeException.class, command()::run).getMessage().contains("incremental"));

		write("models.yaml", CONFIGURATION.replace("models/customer_sales.sql", "models/missing.sql"));
		assertTrue(assertThrows(RuntimeException.class, command()::run).getMessage().contains("missing.sql"));

		write("models.yaml", CONFIGURATION.replace("    materialized: table", "    materialised: table"));
		assertTrue(assertThrows(RuntimeException.class, command()::run).getMessage().contains("materialised"));

		write("models.yaml", CONFIGURATION.replace("      stg_customer:\n", "      stg_customers:\n"));
		assertTrue(assertThrows(RuntimeException.class, command()::run).getMessage().contains("stg_customer"));

		assertEquals(List.of(List.of("raw_order")), query("SELECT table_name FROM information_schema.tables"));
	}
}
