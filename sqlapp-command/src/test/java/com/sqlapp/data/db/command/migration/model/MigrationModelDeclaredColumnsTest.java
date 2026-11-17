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
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.migration.transfer.TransferNameMappingIO;
import com.zaxxer.hikari.HikariDataSource;

/** Declared model columns on embedded DuckDB; no external database is used. */
class MigrationModelDeclaredColumnsTest {
	@TempDir
	Path directory;
	private HikariDataSource dataSource;

	private static final String CONFIGURATION = """
			models:
			  - name: CUSTOMER
			    schema: mart
			    materialized: table
			    query: SELECT * FROM (VALUES (1, 'alice', 1.5)) AS v(ID, NAME, AMOUNT)
			    columns:
			      - name: ID
			        type: INT
			      - name: NAME
			        type: VARCHAR(10)
			        notNull: true
			      - name: AMOUNT
			        type: DECIMAL(8,2)
			    primaryKey: [ID]
			  - name: SUMMARY
			    query: SELECT count(*) AS N FROM /*$ref('CUSTOMER')*/CUSTOMER
			""";

	@BeforeEach
	void setUp() {
		dataSource = new HikariDataSource();
		dataSource.setJdbcUrl("jdbc:duckdb:" + directory.resolve("work.duckdb").toString().replace('\\', '/'));
		dataSource.setMaximumPoolSize(2);
	}

	@AfterEach
	void tearDown() {
		dataSource.close();
	}

	private ExecuteMigrationModelsCommand command(final String yaml) throws Exception {
		Files.writeString(directory.resolve("models.yaml"), yaml, StandardCharsets.UTF_8);
		final ExecuteMigrationModelsCommand command = new ExecuteMigrationModelsCommand();
		command.setConfigurationFile(directory.resolve("models.yaml").toFile());
		command.setDataSource(dataSource);
		command.setCloseDataSource(false);
		command.setTargetMappingFile(directory.resolve("out/model-types.yaml").toFile());
		return command;
	}

	@Test
	void writesTheDeclaredTypesForTheTransfer() throws Exception {
		command(CONFIGURATION).run();

		final var mapping = new TransferNameMappingIO().read(directory.resolve("out/model-types.yaml"));
		assertEquals(
				Map.of("mart.CUSTOMER",
						new TransferNameMappingIO.TableMapping(null,
								Map.of("ID", new TransferNameMappingIO.ColumnMapping(null, "INT", null), "NAME",
										new TransferNameMappingIO.ColumnMapping(null, "VARCHAR(10)", null), "AMOUNT",
										new TransferNameMappingIO.ColumnMapping(null, "DECIMAL(8,2)", null)))),
				mapping.configuration().tables());
		// A second complete build replaces the file without complaint.
		command(CONFIGURATION.replace("VARCHAR(10)", "VARCHAR(20)")).run();
		assertTrue(Files.readString(directory.resolve("out/model-types.yaml")).contains("VARCHAR(20)"));
	}

	@Test
	void failedModelKeepsThePreviousMapping() throws Exception {
		command(CONFIGURATION).run();
		final String before = Files.readString(directory.resolve("out/model-types.yaml"));

		final var command = command(
				CONFIGURATION.replace("'alice'", "'a name that is too long'").replace("VARCHAR(10)", "VARCHAR(5)"));
		final RuntimeException e = assertThrows(RuntimeException.class, command::run);

		assertTrue(e.getMessage().contains("CUSTOMER"), e.getMessage());
		assertEquals(MigrationModelRunner.Status.SKIPPED, command.getResult().models().get(1).status());
		assertEquals(before, Files.readString(directory.resolve("out/model-types.yaml")));
	}

	@Test
	void rejectsInvalidDeclarations() throws Exception {
		assertMessage(CONFIGURATION.replace("type: VARCHAR(10)", "type: VARCHAR"), "needs a length");
		assertMessage(CONFIGURATION.replace("type: INT", "type: INTEGRAL"), "Unknown DataType");
		assertMessage(CONFIGURATION.replace("primaryKey: [ID]", "primaryKey: [CODE]"), "not a declared column");
		assertMessage(CONFIGURATION.replace("    materialized: table\n", ""), "materialization TABLE");
		assertMessage(CONFIGURATION.replace("        type: INT\n", ""), "needs a name and a type");
		assertFalse(Files.exists(directory.resolve("out/model-types.yaml")));
	}

	private void assertMessage(final String yaml, final String expected) throws Exception {
		final RuntimeException e = assertThrows(RuntimeException.class, command(yaml)::run);
		assertTrue(e.getMessage().contains(expected), e.getMessage());
	}
}
