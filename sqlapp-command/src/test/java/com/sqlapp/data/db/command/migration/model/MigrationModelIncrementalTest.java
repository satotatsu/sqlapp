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
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;

import com.sqlapp.exceptions.CommandException;
import com.zaxxer.hikari.HikariDataSource;

/** Incremental models on embedded DuckDB. */
class MigrationModelIncrementalTest {
	/**
	 * EVENT_FACT is incremental and reads the view STG_EVENT. Its relationships
	 * test reads CUSTOMER_DIM, which is built after it.
	 */
	private static final String MODELS = """
			sources:
			  - name: events
			    table: EVENTS
			  - name: customer
			    table: CUSTOMER
			models:
			  - name: STG_EVENT
			    query: SELECT ID, CUSTOMER_ID, AMOUNT, UPDATED_AT FROM /*$source('events')*/events
			  - name: EVENT_FACT
			    materialized: incremental
			    columns:
			      - {name: ID, type: INT}
			      - {name: CUSTOMER_ID, type: INT}
			      - {name: AMOUNT, type: "DECIMAL(10,2)"}
			      - {name: UPDATED_AT, type: TIMESTAMP}
			    primaryKey: [ID]
			    query: |
			      SELECT ID, CUSTOMER_ID, AMOUNT, UPDATED_AT FROM /*$ref('STG_EVENT')*/STG_EVENT
			      /*if isIncremental*/
			      WHERE UPDATED_AT > (SELECT max(UPDATED_AT) FROM /*$ref('EVENT_FACT')*/EVENT_FACT)
			      /*end*/
			    dataTests:
			      - relationships: {column: CUSTOMER_ID, to: CUSTOMER_DIM, field: ID}
			      - expression: AMOUNT >= 0
			  - name: CUSTOMER_DIM
			    materialized: table
			    query: SELECT ID, NAME FROM /*$source('customer')*/customer
			unitTests:
			  - name: builds-from-all-rows
			    model: EVENT_FACT
			    given:
			      STG_EVENT:
			        columns: [ID, CUSTOMER_ID, AMOUNT, UPDATED_AT]
			        rows:
			          - [1, 1, 10, '2026-01-01 00:00:00']
			    expect:
			      rows:
			        - [1, 1, '10', '2026-01-01 00:00:00']
			  - name: adds-newer-rows
			    model: EVENT_FACT
			    incremental: true
			    given:
			      STG_EVENT:
			        sql: >-
			          SELECT * FROM (VALUES (1, 1, 10, TIMESTAMP '2026-01-01 00:00:00'),
			          (2, 1, 20, TIMESTAMP '2026-01-02 00:00:00')) AS t(ID, CUSTOMER_ID, AMOUNT, UPDATED_AT)
			      EVENT_FACT:
			        sql: SELECT TIMESTAMP '2026-01-01 00:00:00' AS UPDATED_AT
			    expect:
			      rows:
			        - [2, 1, '20', '2026-01-02 00:00:00.0']
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
				"CREATE TABLE EVENTS(ID INTEGER, CUSTOMER_ID INTEGER, AMOUNT DECIMAL(10,2), UPDATED_AT TIMESTAMP)",
				"INSERT INTO EVENTS VALUES (1, 1, 10, '2026-01-01'), (2, 2, 20, '2026-01-02')");
	}

	@AfterEach
	void tearDown() {
		dataSource.close();
	}

	@Test
	void testsSeeModelsBuiltLaterOnTheFirstRun() throws Exception {
		final ExecuteMigrationModelsCommand command = run(MODELS, false);
		assertEquals("passed: 2, warned: 0, failed: 0", command.getDataTestResults().summary());
		assertEquals(List.of("STG_EVENT SUCCESS null", "EVENT_FACT SUCCESS built from all rows (no table yet)",
				"CUSTOMER_DIM SUCCESS null"), statuses(command));
		assertEquals(2, command.getUnitTestResults().size());
		assertEquals(List.of("1 10.00", "2 20.00"), rows("SELECT ID, AMOUNT FROM EVENT_FACT ORDER BY ID"));
		assertTrue(fingerprint().startsWith("sqlapp-model:1:sha256:"), fingerprint());
	}

	@Test
	void mergesNewAndChangedRowsByKeyAndKeepsOtherRows() throws Exception {
		run(MODELS, false);
		execute("UPDATE EVENTS SET AMOUNT = 25, UPDATED_AT = '2026-01-03' WHERE ID = 2",
				"INSERT INTO EVENTS VALUES (3, 1, 30, '2026-01-03')",
				// Rows deleted from the source stay in the incremental table.
				"DELETE FROM EVENTS WHERE ID = 1");
		final String before = fingerprint();
		final ExecuteMigrationModelsCommand command = run(MODELS, false);
		assertEquals("EVENT_FACT SUCCESS merged new rows", statuses(command).get(1));
		assertEquals(List.of("1 10.00", "2 25.00", "3 30.00"), rows("SELECT ID, AMOUNT FROM EVENT_FACT ORDER BY ID"));
		assertEquals(before, fingerprint());
	}

	@Test
	void aFailedDataTestRollsBackTheIncrementSoTheSameRangeIsReadAgain() throws Exception {
		run(MODELS, false);
		final String fingerprint = fingerprint();
		execute("INSERT INTO EVENTS VALUES (3, 1, -1, '2026-01-05')");
		final CommandException e = assertThrows(CommandException.class, () -> run(MODELS, false));
		assertTrue(e.getMessage().contains("EVENT_FACT.expression: 1 failing rows"), e.getMessage());
		// Rows, watermark and fingerprint are those of the last committed run.
		assertEquals(List.of("1 10.00", "2 20.00"), rows("SELECT ID, AMOUNT FROM EVENT_FACT ORDER BY ID"));
		assertEquals(List.of("2026-01-02 00:00:00"), rows("SELECT CAST(max(UPDATED_AT) AS VARCHAR) FROM EVENT_FACT"));
		assertEquals(fingerprint, fingerprint());
		// The corrected row keeps its timestamp, so it is only found if the watermark
		// did not move.
		execute("UPDATE EVENTS SET AMOUNT = 5 WHERE ID = 3");
		run(MODELS, false);
		assertEquals(List.of("1 10.00", "2 20.00", "3 5.00"), rows("SELECT ID, AMOUNT FROM EVENT_FACT ORDER BY ID"));
	}

	static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> lineageChanges() {
		return java.util.stream.Stream.of( //
				org.junit.jupiter.params.provider.Arguments.of("own definition",
						"      SELECT ID, CUSTOMER_ID, AMOUNT, UPDATED_AT FROM /*$ref('STG_EVENT')*/STG_EVENT",
						"      SELECT ID, CUSTOMER_ID, AMOUNT * 1 AS AMOUNT, UPDATED_AT FROM /*$ref('STG_EVENT')*/STG_EVENT"),
				org.junit.jupiter.params.provider.Arguments.of("upstream model",
						"AMOUNT, UPDATED_AT FROM /*$source('events')*/events",
						"AMOUNT, UPDATED_AT FROM /*$source('events')*/events WHERE ID > 0"),
				org.junit.jupiter.params.provider.Arguments.of("source definition", "    table: EVENTS\n",
						"    table: EVENTS_V2\n"),
				org.junit.jupiter.params.provider.Arguments.of("declared column",
						"{name: AMOUNT, type: \"DECIMAL(10,2)\"}", "{name: AMOUNT, type: \"DECIMAL(12,2)\"}"));
	}

	@ParameterizedTest
	@org.junit.jupiter.params.provider.MethodSource("lineageChanges")
	void aChangedLineageNeedsFullRefresh(final String change, final String from, final String to) throws Exception {
		execute("CREATE TABLE EVENTS_V2 AS SELECT * FROM EVENTS");
		run(MODELS, false);
		execute("INSERT INTO EVENTS VALUES (3, 1, 30, '2026-01-03')",
				"INSERT INTO EVENTS_V2 VALUES (3, 1, 30, '2026-01-03')");
		final String before = fingerprint();
		final String changed = MODELS.replace(from, to);
		assertFalse(changed.equals(MODELS), change);
		final CommandException e = assertThrows(CommandException.class, () -> run(changed, false), change);
		assertTrue(e.getMessage().contains("Run with fullRefresh"), e.getMessage());
		assertEquals(List.of("1", "2"), rows("SELECT ID FROM EVENT_FACT ORDER BY ID"));
		assertEquals(before, fingerprint());

		final ExecuteMigrationModelsCommand command = run(changed, true);
		assertEquals("EVENT_FACT SUCCESS rebuilt from all rows (fullRefresh)", statuses(command).get(1));
		assertEquals(List.of("1", "2", "3"), rows("SELECT ID FROM EVENT_FACT ORDER BY ID"));
		assertFalse(before.equals(fingerprint()), change);
		// The new fingerprint is recorded: the next run adds rows again.
		assertEquals("EVENT_FACT SUCCESS merged new rows", statuses(run(changed, false)).get(1));
	}

	@Test
	void anUnrelatedModelDoesNotChangeTheLineage() throws Exception {
		run(MODELS, false);
		final ExecuteMigrationModelsCommand command = run(
				MODELS.replace("SELECT ID, NAME FROM /*$source('customer')*/customer",
						"SELECT ID, upper(NAME) AS NAME FROM /*$source('customer')*/customer"),
				false);
		assertEquals("EVENT_FACT SUCCESS merged new rows", statuses(command).get(1));
	}

	@Test
	void aTableWithoutFingerprintNeedsFullRefresh() throws Exception {
		execute("CREATE TABLE EVENT_FACT(ID INTEGER PRIMARY KEY, CUSTOMER_ID INTEGER, AMOUNT DECIMAL(10,2),"
				+ " UPDATED_AT TIMESTAMP)");
		final CommandException e = assertThrows(CommandException.class, () -> run(MODELS, false));
		assertTrue(e.getMessage().contains("has no lineage fingerprint"), e.getMessage());
		run(MODELS, true);
		assertEquals(List.of("1", "2"), rows("SELECT ID FROM EVENT_FACT ORDER BY ID"));
	}

	@Test
	void duplicatedOrNullKeysAreRejectedWithoutValues() throws Exception {
		run(MODELS, false);
		execute("INSERT INTO EVENTS VALUES (7, 1, 70.70, '2026-01-04'), (7, 2, 71.71, '2026-01-04'),"
				+ " (NULL, 1, 72.72, '2026-01-04')");
		final CommandException e = assertThrows(CommandException.class, () -> run(MODELS, false));
		assertTrue(e.getMessage().contains("1 rows with a NULL key, 1 duplicated keys"), e.getMessage());
		for (final String value : new String[] { "70.7", "71.71", "72.72" }) {
			assertFalse(e.getMessage().contains(value), e.getMessage());
		}
		assertEquals(List.of("1", "2"), rows("SELECT ID FROM EVENT_FACT ORDER BY ID"));
	}

	@Test
	void rejectsInvalidIncrementalDefinitions() throws Exception {
		Files.writeString(directory.resolve("models.yaml"), MODELS.replace("    primaryKey: [ID]\n", ""),
				StandardCharsets.UTF_8);
		var e = assertThrows(CommandException.class,
				() -> new MigrationModelConfigurationResolver().resolve(directory.resolve("models.yaml").toFile()));
		assertTrue(e.getMessage().contains("needs declared columns and a primary key"), e.getMessage());
		Files.writeString(directory.resolve("models.yaml"),
				MODELS.replace("SELECT ID, NAME FROM /*$source('customer')*/customer",
						"SELECT ID, NAME FROM /*$ref('CUSTOMER_DIM')*/CUSTOMER_DIM"),
				StandardCharsets.UTF_8);
		e = assertThrows(CommandException.class,
				() -> new MigrationModelConfigurationResolver().resolve(directory.resolve("models.yaml").toFile()));
		assertTrue(e.getMessage().contains("Only an incremental model may read its own table"), e.getMessage());
	}

	private ExecuteMigrationModelsCommand run(final String models, final boolean fullRefresh) throws Exception {
		Files.writeString(directory.resolve("models.yaml"), models, StandardCharsets.UTF_8);
		final ExecuteMigrationModelsCommand command = new ExecuteMigrationModelsCommand();
		command.setConfigurationFile(directory.resolve("models.yaml").toFile());
		command.setDataSource(dataSource);
		command.setCloseDataSource(false);
		command.setFullRefresh(fullRefresh);
		command.run();
		return command;
	}

	private static List<String> statuses(final ExecuteMigrationModelsCommand command) {
		return command.getResult().models().stream()
				.map(model -> model.name() + " " + model.status() + " " + model.message()).toList();
	}

	private String fingerprint() throws Exception {
		return rows("SELECT comment FROM duckdb_tables() WHERE table_name = 'EVENT_FACT'").getFirst();
	}

	private void execute(final String... sql) throws Exception {
		try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
			for (final String text : sql) {
				statement.execute(text);
			}
		}
	}

	private List<String> rows(final String sql) throws Exception {
		final List<String> rows = new ArrayList<>();
		try (var connection = dataSource.getConnection();
				var statement = connection.createStatement();
				var resultSet = statement.executeQuery(sql)) {
			final int count = resultSet.getMetaData().getColumnCount();
			while (resultSet.next()) {
				final StringBuilder row = new StringBuilder();
				for (int i = 1; i <= count; i++) {
					row.append(i == 1 ? "" : " ").append(resultSet.getString(i));
				}
				rows.add(row.toString());
			}
		}
		return rows;
	}
}
