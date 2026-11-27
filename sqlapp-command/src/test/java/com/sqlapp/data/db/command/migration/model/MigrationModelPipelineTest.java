/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.migration.transfer.TransferDatabaseCommand;
import com.sqlapp.data.db.command.migration.transfer.TransferJobConfigurationIO;
import com.sqlapp.data.db.command.migration.transfer.TransferJobExecutionService;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Source database to DuckDB, models in DuckDB, model table to a target
 * database, using only the existing commands. Embedded HSQLDB and DuckDB; no
 * external database is used.
 */
class MigrationModelPipelineTest {
	@TempDir
	Path directory;

	@Test
	void transfersBuildsModelsAndLoadsTheResult() throws Exception {
		final JDBCDataSource source = hsql();
		final JDBCDataSource target = hsql();
		try (HikariDataSource work = new HikariDataSource()) {
			work.setJdbcUrl("jdbc:duckdb:" + directory.resolve("work.duckdb").toString().replace('\\', '/'));
			work.setMaximumPoolSize(2);
			execute(source, "CREATE TABLE CUSTOMER(ID INTEGER NOT NULL PRIMARY KEY, NAME VARCHAR(50) NOT NULL)",
					"CREATE TABLE ORDERS(ID INTEGER NOT NULL PRIMARY KEY,"
							+ " CUSTOMER_ID INTEGER NOT NULL REFERENCES CUSTOMER(ID), AMOUNT DECIMAL(10,2))",
					"INSERT INTO CUSTOMER VALUES (1, 'alice'), (2, 'bob')",
					"INSERT INTO ORDERS VALUES (10, 1, 150.25), (11, 1, 49.75), (12, 2, 120.00)");

			// 1. Source to DuckDB, verified under DuckDB's snapshot isolation. DuckDB
			// tables are created from the source Schema, including the foreign key.
			final TransferDatabaseCommand load = transfer("load", source, work, """
					formatVersion: 1
					source: src
					target: work
					tables: [CUSTOMER, ORDERS]
					schemaMode: CREATE_MISSING
					""");
			assertTrue(load.getResult().verificationResult().isMatch());
			assertEquals(List.of("3"), rows(work, "SELECT count(*) FROM ORDERS"));

			// 2. Models in DuckDB.
			Files.writeString(directory.resolve("models.yaml"), """
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
					      SELECT c.ID AS CUSTOMER_ID, c.NAME, CAST(sum(o.AMOUNT) AS DECIMAL(12,2)) AS TOTAL
					      FROM /*$source('customer')*/customer c
					      JOIN /*$source('orders')*/orders o ON o.CUSTOMER_ID = c.ID
					      GROUP BY c.ID, c.NAME
					""", StandardCharsets.UTF_8);
			final ExecuteMigrationModelsCommand models = new ExecuteMigrationModelsCommand();
			models.setConfigurationFile(directory.resolve("models.yaml").toFile());
			models.setDataSource(work);
			models.setCloseDataSource(false);
			models.setTargetMappingFile(directory.resolve("model-types.yaml").toFile());
			models.run();
			assertTrue(models.getResult().isSuccess());

			// 3. Model table to the target, with verification. The target table is
			// created with the declared types: DuckDB itself keeps no string lengths.
			final TransferDatabaseCommand unload = transfer("unload", work, target, """
					formatVersion: 1
					source: work
					target: dst
					tables: [CUSTOMER_SALES]
					schemaMode: CREATE_MISSING
					mappingFile: %s
					""".formatted(directory.resolve("model-types.yaml").toString().replace('\\', '/')));
			assertTrue(unload.getResult().verificationResult().isMatch());
			assertEquals(List.of("1 | alice | 200.00", "2 | bob | 120.00"),
					rows(target, "SELECT * FROM CUSTOMER_SALES ORDER BY CUSTOMER_ID"));
			assertEquals(
					List.of("CUSTOMER_ID | INTEGER | null | NO", "NAME | VARCHAR | 50 | NO",
							"TOTAL | DECIMAL | 12 | YES"),
					rows(target,
							"SELECT COLUMN_NAME, TYPE_NAME, CASE WHEN TYPE_NAME = 'INTEGER' THEN NULL"
									+ " ELSE COLUMN_SIZE END, IS_NULLABLE FROM INFORMATION_SCHEMA.SYSTEM_COLUMNS"
									+ " WHERE TABLE_NAME = 'CUSTOMER_SALES' ORDER BY ORDINAL_POSITION"));
			assertEquals(List.of("CUSTOMER_ID"), rows(target, "SELECT COLUMN_NAME FROM"
					+ " INFORMATION_SCHEMA.SYSTEM_PRIMARYKEYS WHERE TABLE_NAME = 'CUSTOMER_SALES'"));
		}
	}

	private TransferDatabaseCommand transfer(final String name, final DataSource from, final DataSource to,
			final String yaml) throws Exception {
		final Path job = Files.writeString(directory.resolve(name + ".yaml"), yaml, StandardCharsets.UTF_8);
		final TransferDatabaseCommand command = new TransferDatabaseCommand();
		command.setConfigurationFile(job.toFile());
		command.setWorkspaceDirectory(directory.resolve(name).toFile());
		command.setSourceDataSource(from);
		command.setDataSource(to);
		command.setCloseDataSource(false);
		command.setAllowExecution(true);
		command.setExpectedConfigurationFingerprint(new TransferJobConfigurationIO().read(job).fingerprint());
		command.run();
		assertEquals(TransferJobExecutionService.Phase.COMPLETE, command.getResult().phase());
		return command;
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
