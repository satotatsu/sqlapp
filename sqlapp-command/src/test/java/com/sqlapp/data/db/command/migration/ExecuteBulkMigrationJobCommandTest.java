/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.db.command.test.AbstractDbCommandTest;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlanner;
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
			configuration.setTasks(List.of(task));
			final File configurationFile = temporaryDirectory.resolve("job.yaml").toFile();
			new YamlConverter().writeJsonValue(configurationFile, configuration);
			try (var connection = source.getConnection()) {
				final var plan = new BulkMigrationJobConfigurationResolver()
						.resolve(configurationFile, connection);
				assertEquals(List.of("items"), plan.getTaskIds());
			}

			configuration.setTasks(List.of());
			new YamlConverter().writeJsonValue(configurationFile, configuration);

			final var command = new ExecuteBulkMigrationJobCommand();
			command.setDataSource(target);
			command.setSourceDataSource(source);
			command.setCloseDataSource(false);
			command.setConfigurationFile(configurationFile);
			command.run();

			assertEquals(0, command.getResult().getTasks().size());
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

	private static HikariDataSource dataSource(final String name) {
		final HikariConfig config = new HikariConfig();
		config.setJdbcUrl("jdbc:hsqldb:mem:" + name);
		config.setUsername("SA");
		config.setPassword("");
		return new HikariDataSource(config);
	}
}
