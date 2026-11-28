/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.sqlapp.data.db.command.migration.model.MigrationModelDataTests.Status;
import com.sqlapp.exceptions.CommandException;
import com.zaxxer.hikari.HikariDataSource;

/** Data tests on embedded DuckDB. */
class MigrationModelDataTestsTest {
	private static final String MODELS = """
			sources:
			  - name: customer
			    table: CUSTOMER
			  - name: orders
			    table: ORDERS
			models:
			  - name: ORDER_FACT
			    materialized: table
			    columns:
			      - {name: ORDER_ID, type: INT}
			      - {name: CUSTOMER_ID, type: INT}
			      - {name: STATUS, type: VARCHAR(10)}
			      - {name: AMOUNT, type: "DECIMAL(10,2)"}
			    query: SELECT ORDER_ID, CUSTOMER_ID, STATUS, AMOUNT FROM /*$source('orders')*/orders
			    dataTests:
			      - notNull: [CUSTOMER_ID, AMOUNT]
			      - unique: [ORDER_ID]
			      - acceptedValues: {column: STATUS, values: [NEW, SHIPPED]}
			      - relationships: {column: CUSTOMER_ID, to: customer, field: ID}
			      - expression: AMOUNT >= 0
			      - rowCount: {min: 1, max: 3}
			      - name: no-cancelled
			        severity: warn
			        sql: SELECT * FROM /*$ref('ORDER_FACT')*/ORDER_FACT WHERE STATUS = 'CANCELLED'
			""";

	@TempDir
	Path directory;
	private HikariDataSource dataSource;

	@BeforeEach
	void setUp() throws Exception {
		dataSource = new HikariDataSource();
		dataSource.setJdbcUrl("jdbc:duckdb:" + directory.resolve("work.duckdb").toString().replace('\\', '/'));
		dataSource.setMaximumPoolSize(2);
		execute("CREATE TABLE CUSTOMER(ID INTEGER, NAME VARCHAR)", "INSERT INTO CUSTOMER VALUES (1, 'a'), (2, 'b')",
				"CREATE TABLE ORDERS(ORDER_ID INTEGER, CUSTOMER_ID INTEGER, STATUS VARCHAR, AMOUNT DECIMAL(10,2))");
	}

	@AfterEach
	void tearDown() {
		dataSource.close();
	}

	@Test
	void passesWhenEveryRuleHolds() throws Exception {
		execute("INSERT INTO ORDERS VALUES (10, 1, 'NEW', 150.25), (11, 2, 'SHIPPED', 0)");
		final ExecuteMigrationModelsCommand command = command(MODELS);
		command.setStateFile(directory.resolve("state.json").toFile());
		command.run();
		assertTrue(command.getDataTestResults().isSuccess());
		assertEquals(7, command.getDataTestResults().count(Status.PASS));
		assertEquals("passed: 7, warned: 0, failed: 0", command.getDataTestResults().summary());
		assertTrue(Files.isRegularFile(directory.resolve("state.json")));
	}

	@Test
	void reportsFailingRowsWithoutValuesAndRollsBackTheModels() throws Exception {
		execute("INSERT INTO ORDERS VALUES (10, 1, 'NEW', 150.25), (10, 2, 'NEW', 1), (11, 3, 'BAD', -5),"
				+ " (12, NULL, 'NEW', 10), (13, 1, 'CANCELLED', NULL)");
		final ExecuteMigrationModelsCommand command = command(MODELS);
		command.setStateFile(directory.resolve("state.json").toFile());
		final CommandException e = assertThrows(CommandException.class, command::run);
		final Map<String, String> outcomes = new LinkedHashMap<>();
		command.getDataTestResults().tests()
				.forEach(test -> outcomes.put(test.id(), test.status() + " " + test.failingRows()));
		assertEquals(Map.of( //
				"ORDER_FACT.not_null(CUSTOMER_ID,AMOUNT)", "FAIL 2", // 12 and 13
				"ORDER_FACT.unique(ORDER_ID)", "FAIL 1", // key 10
				"ORDER_FACT.accepted_values(STATUS)", "FAIL 2", // BAD and CANCELLED
				"ORDER_FACT.relationships(CUSTOMER_ID)", "FAIL 1", // 3; NULL is not tested
				"ORDER_FACT.expression", "FAIL 2", // -5 and NULL
				"ORDER_FACT.row_count", "FAIL 5", //
				"no-cancelled", "WARN 1"), outcomes);
		assertTrue(e.getMessage().contains("ORDER_FACT.unique(ORDER_ID): 1 duplicated keys"), e.getMessage());
		assertTrue(e.getMessage().contains("ORDER_FACT.row_count: row count 5 is outside [1, 3]"), e.getMessage());
		assertFalse(e.getMessage().contains("no-cancelled"), "a warning does not fail the run: " + e.getMessage());
		for (final String value : new String[] { "BAD", "CANCELLED", "-5", "150.25" }) {
			assertFalse(e.getMessage().contains(value), value + " in " + e.getMessage());
		}
		// The models of the run are rolled back and the state file is not written.
		assertTrue(e.getMessage().contains("rolled back"), e.getMessage());
		assertEquals(0L, count("SELECT count(*) FROM information_schema.tables WHERE table_name = 'ORDER_FACT'"));
		assertFalse(Files.exists(directory.resolve("state.json")));
	}

	@Test
	void reportsATestQueryThatCannotRunAsAnError() throws Exception {
		execute("INSERT INTO ORDERS VALUES (10, 1, 'NEW', 1)");
		final ExecuteMigrationModelsCommand command = command(MODELS.replace(
				"SELECT * FROM /*$ref('ORDER_FACT')*/ORDER_FACT WHERE STATUS = 'CANCELLED'", "SELECT * FROM missing"));
		// A WARN test that cannot run still fails the run.
		final CommandException e = assertThrows(CommandException.class, command::run);
		final var test = command.getDataTestResults().tests().stream()
				.filter(value -> value.id().equals("no-cancelled")).findFirst().orElseThrow();
		assertEquals(Status.ERROR, test.status());
		assertTrue(e.getMessage().contains("no-cancelled"), e.getMessage());
		assertEquals("passed: 6, warned: 0, failed: 1", command.getDataTestResults().summary());
	}

	@Test
	void aFailedTestQueryReportsNoDatabaseMessage() throws Exception {
		// DuckDB quotes the value that cannot be cast in its error message.
		execute("INSERT INTO ORDERS VALUES (10, 1, 'NEW', 1), (11, 2, 'SHIPPED', 2)",
				"UPDATE CUSTOMER SET NAME = 'SECRET-VALUE-42' WHERE ID = 1");
		final ExecuteMigrationModelsCommand command = command(
				MODELS.replace("SELECT * FROM /*$ref('ORDER_FACT')*/ORDER_FACT WHERE STATUS = 'CANCELLED'",
						"SELECT * FROM /*$source('customer')*/customer WHERE CAST(NAME AS INTEGER) > 0"));
		final CommandException e = assertThrows(CommandException.class, command::run);
		final var test = command.getDataTestResults().tests().stream()
				.filter(value -> value.id().equals("no-cancelled")).findFirst().orElseThrow();
		assertEquals(Status.ERROR, test.status());
		assertTrue(test.message().startsWith("test query failed (SQLException"), test.message());
		assertFalse(test.message().contains("SECRET-VALUE-42"), test.message());
		assertFalse(e.getMessage().contains("SECRET-VALUE-42"), e.getMessage());
	}

	@Test
	void modifiedOnlyRunsEveryTestAlsoForModelsThatWereNotRebuilt() throws Exception {
		execute("INSERT INTO ORDERS VALUES (10, 1, 'NEW', 1), (11, 2, 'SHIPPED', 2)");
		final ExecuteMigrationModelsCommand first = command(MODELS);
		first.setStateFile(directory.resolve("state.json").toFile());
		first.run();
		// Only a test changes: no model is rebuilt, the changed test still runs.
		final ExecuteMigrationModelsCommand second = command(MODELS.replace("{min: 1, max: 3}", "{min: 3}"));
		second.setStateFile(directory.resolve("state.json").toFile());
		second.setModifiedOnly(true);
		final CommandException e = assertThrows(CommandException.class, second::run);
		assertEquals(0, second.getResult().models().size());
		assertTrue(e.getMessage().contains("ORDER_FACT.row_count: row count 2 is outside [3, ]"), e.getMessage());
		assertEquals(7, second.getDataTestResults().tests().size());
	}

	@Test
	void canBeTurnedOff() throws Exception {
		execute("INSERT INTO ORDERS VALUES (10, 3, 'BAD', -5)");
		final ExecuteMigrationModelsCommand command = command(MODELS);
		command.setDataTestsEnabled(false);
		command.run();
		assertEquals(null, command.getDataTestResults());
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { //
			"{notNull: [ORDER_ID], unique: [ORDER_ID]}|needs exactly one of",
			"{relationships: {column: CUSTOMER_ID, to: nothing, field: ID}}|unknown model or source: nothing",
			"{notNull: [MISSING]}|does not declare: MISSING",
			"{acceptedValues: {column: STATUS, values: []}}|needs at least one value",
			"{rowCount: {min: 5, max: 1}}|min must not exceed max",
			"{expression: 'AMOUNT > 0', severity: fatal}|Use error or warn",
			"{relationships: {columns: [CUSTOMER_ID], to: customer, fields: [ID, NAME]}}|same number" })
	void rejectsInvalidDefinitions(final String test, final String expected) throws Exception {
		final String models = MODELS.replace("      - notNull: [CUSTOMER_ID, AMOUNT]", "      - " + test);
		Files.writeString(directory.resolve("models.yaml"), models, StandardCharsets.UTF_8);
		final CommandException e = assertThrows(CommandException.class,
				() -> new MigrationModelConfigurationResolver().resolve(directory.resolve("models.yaml").toFile()));
		assertTrue(e.getMessage().contains(expected), e.getMessage());
	}

	@Test
	void rejectsDuplicateNames() throws Exception {
		final String models = MODELS.replace("      - unique: [ORDER_ID]", "      - notNull: [CUSTOMER_ID, AMOUNT]");
		Files.writeString(directory.resolve("models.yaml"), models, StandardCharsets.UTF_8);
		final CommandException e = assertThrows(CommandException.class,
				() -> new MigrationModelConfigurationResolver().resolve(directory.resolve("models.yaml").toFile()));
		assertTrue(e.getMessage().contains("Duplicate data test name"), e.getMessage());
	}

	private ExecuteMigrationModelsCommand command(final String models) throws Exception {
		Files.writeString(directory.resolve("models.yaml"), models, StandardCharsets.UTF_8);
		final ExecuteMigrationModelsCommand command = new ExecuteMigrationModelsCommand();
		command.setConfigurationFile(directory.resolve("models.yaml").toFile());
		command.setDataSource(dataSource);
		command.setCloseDataSource(false);
		return command;
	}

	private void execute(final String... sql) throws Exception {
		try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
			for (final String text : sql) {
				statement.execute(text);
			}
		}
	}

	private long count(final String sql) throws Exception {
		try (var connection = dataSource.getConnection();
				var statement = connection.createStatement();
				var resultSet = statement.executeQuery(sql)) {
			resultSet.next();
			return resultSet.getLong(1);
		}
	}
}
