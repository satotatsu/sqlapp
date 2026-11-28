/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.sqlapp.data.db.command.migration.transfer.TransferDatabaseCommand;
import com.sqlapp.data.db.command.migration.transfer.TransferJobConfigurationIO;
import com.sqlapp.data.db.command.migration.transfer.TransferJobExecutionService;
import com.sqlapp.data.db.command.migration.transfer.TransferStagedReplacement;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Publishing declared model tables into existing target tables: the contract
 * check before loading, and a load that the target rejects. DuckDB and HSQLDB
 * are embedded.
 */
class MigrationModelPublishTest {
	@TempDir
	Path directory;

	private HikariDataSource work;
	private JDBCDataSource target;

	@BeforeEach
	void buildModels() throws Exception {
		work = new HikariDataSource();
		work.setJdbcUrl("jdbc:duckdb:" + directory.resolve("work.duckdb").toString().replace('\\', '/'));
		work.setMaximumPoolSize(2);
		target = new JDBCDataSource();
		target.setUrl("jdbc:hsqldb:mem:publish_" + UUID.randomUUID());
		target.setUser("SA");
		target.setPassword("");
		// 12 kana: 12 characters fit VARCHAR(20), 24 bytes in UTF-16 do not fit 20
		// bytes.
		execute(work,
				"CREATE TABLE CUSTOMER(ID INTEGER NOT NULL PRIMARY KEY, NAME VARCHAR NOT NULL,"
						+ " AMOUNT DECIMAL(12,2))",
				"INSERT INTO CUSTOMER VALUES (1, 'alice', 150.25), (2, 'やまだたろうやまだたろう', 120.00)");
		Files.writeString(directory.resolve("models.yaml"), """
				sources:
				  - name: customer
				    table: CUSTOMER
				models:
				  - name: CUSTOMER_SALES
				    materialized: table
				    columns:
				      - {name: CUSTOMER_ID, type: INT}
				      - {name: NAME, type: VARCHAR(20), notNull: true}
				      - {name: TOTAL, type: "DECIMAL(12,2)"}
				    primaryKey: [CUSTOMER_ID]
				    query: |
				      SELECT ID AS CUSTOMER_ID, NAME, AMOUNT AS TOTAL FROM /*$source('customer')*/customer
				  - name: CUSTOMER_NAMES
				    materialized: table
				    columns:
				      - {name: ID, type: INT}
				      - {name: NAME, type: VARCHAR(20)}
				    primaryKey: [ID]
				    query: |
				      SELECT ID, NAME FROM /*$source('customer')*/customer
				""", StandardCharsets.UTF_8);
		final ExecuteMigrationModelsCommand models = new ExecuteMigrationModelsCommand();
		models.setConfigurationFile(directory.resolve("models.yaml").toFile());
		models.setDataSource(work);
		models.setCloseDataSource(false);
		models.setTargetMappingFile(directory.resolve("model-types.yaml").toFile());
		models.run();
		assertTrue(models.getResult().isSuccess());
	}

	@AfterEach
	void close() throws Exception {
		work.close();
		execute(target, "SHUTDOWN");
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { //
			"CUSTOMER_ID INTEGER NOT NULL PRIMARY KEY, NAME VARCHAR(20) NOT NULL, TOTAL DECIMAL(12,1)"
					+ "|TOTAL: DECIMAL(12,2) does not fit DECIMAL(12,1)",
			"CUSTOMER_ID INTEGER NOT NULL PRIMARY KEY, NAME VARCHAR(20) NOT NULL, TOTAL DECIMAL(11,2)"
					+ "|TOTAL: DECIMAL(12,2) does not fit DECIMAL(11,2)",
			"CUSTOMER_ID INTEGER NOT NULL PRIMARY KEY, NAME VARCHAR(10) NOT NULL, TOTAL DECIMAL(12,2)"
					+ "|NAME: VARCHAR(20) does not fit VARCHAR(10)",
			"CUSTOMER_ID INTEGER NOT NULL PRIMARY KEY, NAME VARCHAR(20) NOT NULL, TOTAL INTEGER"
					+ "|TOTAL: DECIMAL(12,2) does not fit INT",
			"CUSTOMER_ID SMALLINT NOT NULL PRIMARY KEY, NAME VARCHAR(20) NOT NULL, TOTAL DECIMAL(12,2)"
					+ "|CUSTOMER_ID: INT does not fit SMALLINT",
			"CUSTOMER_ID INTEGER NOT NULL PRIMARY KEY, NAME VARCHAR(20) NOT NULL, TOTAL DECIMAL(12,2) NOT NULL"
					+ "|TOTAL: NOT NULL in the target",
			"CUSTOMER_ID INTEGER NOT NULL PRIMARY KEY, NAME VARCHAR(20) NOT NULL, TOTAL DECIMAL(12,2),"
					+ " NOTE VARCHAR(10) NOT NULL|NOTE: NOT NULL target column without a default",
			"CUSTOMER_ID INTEGER NOT NULL PRIMARY KEY, NAME VARCHAR(20) NOT NULL UNIQUE, TOTAL DECIMAL(12,2)"
					+ "|unique key [NAME]" })
	void stopsBeforeLoadingWhenAnExistingTableBreaksTheContract(final String columns, final String expected)
			throws Exception {
		execute(target, "CREATE TABLE CUSTOMER_SALES(" + columns + ")");
		final TransferDatabaseCommand command = transfer("""
				tables: [CUSTOMER_SALES, CUSTOMER_NAMES]
				schemaMode: CREATE_MISSING
				""");
		final Exception e = assertThrows(Exception.class, command::run);
		assertTrue(e.getMessage().contains(expected), e.getMessage());
		assertTrue(e.getMessage().contains("Nothing was written"), e.getMessage());
		assertEquals(List.of("0"), rows(target, "SELECT count(*) FROM CUSTOMER_SALES"));
	}

	@Test
	void loadsIntoAnExistingTableThatAcceptsTheContract() throws Exception {
		// Wider types, a unique key that contains the declared key, a nullable extra
		// column.
		execute(target, "CREATE TABLE CUSTOMER_SALES(CUSTOMER_ID BIGINT NOT NULL PRIMARY KEY,"
				+ " NAME VARCHAR(100) NOT NULL, TOTAL DECIMAL(15,2), NOTE VARCHAR(10), UNIQUE (CUSTOMER_ID, NAME))",
				"CREATE TABLE CUSTOMER_NAMES(ID INTEGER NOT NULL PRIMARY KEY, NAME CLOB)");
		final TransferDatabaseCommand command = transfer("""
				tables: [CUSTOMER_SALES, CUSTOMER_NAMES]
				schemaMode: REQUIRE_EXISTING
				""");
		command.run();
		assertEquals(TransferJobExecutionService.Phase.COMPLETE, command.getResult().phase());
		assertTrue(command.getResult().verificationResult().isMatch());
		assertEquals(List.of("1 | alice | 150.25 | null", "2 | やまだたろうやまだたろう | 120.00 | null"),
				rows(target, "SELECT * FROM CUSTOMER_SALES ORDER BY CUSTOMER_ID"));
	}

	@Test
	void rejectedRowLeavesPublishedRowsAndKeepsTheNewTableEmpty() throws Exception {
		// The target counts bytes: the contract check passes, the load is rejected.
		execute(target,
				"CREATE TABLE CUSTOMER_SALES(CUSTOMER_ID INTEGER NOT NULL PRIMARY KEY,"
						+ " NAME VARCHAR(20) NOT NULL CHECK (OCTET_LENGTH(NAME) <= 20), TOTAL DECIMAL(12,2))",
				"INSERT INTO CUSTOMER_SALES VALUES (99, 'published', 1.00)");
		final TransferDatabaseCommand command = transfer("""
				tables: [CUSTOMER_SALES, CUSTOMER_NAMES]
				schemaMode: CREATE_MISSING
				targetPreparation: STAGED_REPLACE
				""");
		final var failure = assertThrows(TransferJobExecutionService.ExecutionException.class, command::run);
		// Data: the replacement rolled back, so the published rows are unchanged.
		assertEquals(TransferStagedReplacement.State.ROLLED_BACK, failure.outcome().staging().state());
		assertEquals(List.of("99 | published | 1.00"), rows(target, "SELECT * FROM CUSTOMER_SALES"));
		// Schema: the table created for the new model remains, without rows.
		assertEquals(List.of("0"), rows(target, "SELECT count(*) FROM CUSTOMER_NAMES"));
	}

	private TransferDatabaseCommand transfer(final String options) throws Exception {
		final Path job = Files.writeString(directory.resolve("unload.yaml"), """
				formatVersion: 1
				source: work
				target: dst
				mappingFile: %s
				""".formatted(directory.resolve("model-types.yaml").toString().replace('\\', '/')) + options,
				StandardCharsets.UTF_8);
		final TransferDatabaseCommand command = new TransferDatabaseCommand();
		command.setConfigurationFile(job.toFile());
		command.setWorkspaceDirectory(directory.resolve("unload").toFile());
		command.setSourceDataSource(work);
		command.setDataSource(target);
		command.setCloseDataSource(false);
		command.setAllowExecution(true);
		command.setExpectedConfigurationFingerprint(new TransferJobConfigurationIO().read(job).fingerprint());
		return command;
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
