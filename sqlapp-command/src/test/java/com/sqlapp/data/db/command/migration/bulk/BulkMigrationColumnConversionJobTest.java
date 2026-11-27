/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Path;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpointMode;
import com.sqlapp.jdbc.bulk.ConvertingJdbcBulkMigrationKeysetSource;
import com.sqlapp.util.YamlConverter;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Declarative bulk jobs with column value conversions on embedded databases.
 */
class BulkMigrationColumnConversionJobTest {
	@TempDir
	Path directory;

	@Test
	void copiesVerifiesAndPinsConversionsIntoResumeCheckpoints() throws Exception {
		try (var source = dataSource(); var target = dataSource()) {
			sql(source, "CREATE TABLE PUBLIC.ITEMS (ID INT NOT NULL PRIMARY KEY, NAME VARCHAR(30), PRICE INT)",
					"INSERT INTO PUBLIC.ITEMS VALUES (1, ' one ', 10), (2, 'two', NULL), (3, NULL, 30)");
			sql(target, "CREATE TABLE PUBLIC.ITEMS (ID INT NOT NULL PRIMARY KEY, NAME VARCHAR(30), PRICE INT)");
			var configuration = configuration(
					Map.of("NAME", "value == null ? '-' : value.trim()", "PRICE", "value == null ? 0 : value * 2"));
			var file = write(configuration);
			try (var connection = source.getConnection()) {
				var plan = new BulkMigrationJobConfigurationResolver().resolve(file, connection);
				var task = plan.getTasks().getFirst();
				assertInstanceOf(ConvertingJdbcBulkMigrationKeysetSource.class, task.getKeysetSource());
				assertTrue(task.getOptions().getSourceFingerprint().matches("columnConversions=sha256:[0-9a-f]{64}"),
						task.getOptions().getSourceFingerprint());
			}
			var command = command(file, source, target);
			command.run();
			assertTrue(command.getVerificationResult().isMatch());
			assertEquals(List.of("1|one|20", "2|two|0", "3|-|60"), rows(target));
			// Same reviewed conversions: the completed checkpoint is reused.
			command(file, source, target).run();
			// Changed conversions cannot resume or skip work recorded by older ones.
			configuration.getTasks().getFirst().setColumnConversions(Map.of("NAME", "value"));
			var changed = write(configuration);
			var failure = assertThrows(RuntimeException.class, () -> command(changed, source, target).run());
			assertTrue(messages(failure).contains("Checkpoint fingerprints do not match"), messages(failure));
		}
	}

	@Test
	void copyTypesLetConversionsProduceTheTargetRepresentation() throws Exception {
		try (var source = dataSource(); var target = dataSource()) {
			sql(source, "CREATE TABLE PUBLIC.ITEMS (ID INT NOT NULL PRIMARY KEY, NAME VARCHAR(30), PRICE INT)",
					"INSERT INTO PUBLIC.ITEMS VALUES (1, 'one', 10), (2, 'two', NULL), (3, 'three', 30)");
			sql(target, "CREATE TABLE PUBLIC.ITEMS (ID INT NOT NULL PRIMARY KEY, NAME VARCHAR(30), PRICE VARCHAR(20))");
			var configuration = configuration(Map.of("PRICE", "value == null ? '-' : 'JPY ' + value"));
			configuration.getTasks().getFirst().setColumnTypes(Map.of("PRICE", "VARCHAR(20)"));
			var file = write(configuration);
			var command = command(file, source, target);
			command.run();
			assertTrue(command.getVerificationResult().isMatch());
			assertEquals(List.of("1|one|JPY 10", "2|two|-", "3|three|JPY 30"), rows(target));
			try (var connection = source.getConnection()) {
				var first = new BulkMigrationJobConfigurationResolver().resolve(file, connection).getTasks().getFirst()
						.getOptions().getSourceFingerprint();
				configuration.getTasks().getFirst().setColumnTypes(Map.of("PRICE", "VARCHAR(21)"));
				var second = new BulkMigrationJobConfigurationResolver().resolve(write(configuration), connection)
						.getTasks().getFirst().getOptions().getSourceFingerprint();
				assertNotEquals(first, second);
				configuration.getTasks().getFirst().setColumnTypes(Map.of("ID", "BIGINT"));
				var key = write(configuration);
				assertTrue(assertThrows(CommandException.class,
						() -> new BulkMigrationJobConfigurationResolver().resolve(key, connection)).getMessage()
						.contains("Copy types cannot change primary key column ITEMS.ID"));
				configuration.getTasks().getFirst().setColumnTypes(Map.of("PRICE", "NOSUCHTYPE(1)"));
				var unknown = write(configuration);
				assertTrue(assertThrows(CommandException.class,
						() -> new BulkMigrationJobConfigurationResolver().resolve(unknown, connection)).getMessage()
						.contains("Invalid column type 'NOSUCHTYPE(1)'"));
			}
		}
	}

	@Test
	void sourceFingerprintKeepsTheConfiguredValueAndRejectsKeyConversions() throws Exception {
		try (var source = dataSource()) {
			sql(source, "CREATE TABLE PUBLIC.ITEMS (ID INT NOT NULL PRIMARY KEY, NAME VARCHAR(30), PRICE INT)",
					"CREATE UNIQUE INDEX UX_ITEMS_NAME ON PUBLIC.ITEMS(NAME)");
			var configuration = configuration(Map.of("PRICE", "value"));
			configuration.getTasks().getFirst().setSourceFingerprint("extract-v7");
			try (var connection = source.getConnection()) {
				var options = new BulkMigrationJobConfigurationResolver().resolve(write(configuration), connection)
						.getTasks().getFirst().getOptions();
				assertTrue(options.getSourceFingerprint().startsWith("extract-v7;columnConversions=sha256:"));
				for (var invalid : List.of(Map.of("ID", "value"), Map.of("UNKNOWN", "value"))) {
					configuration.getTasks().getFirst().setColumnConversions(invalid);
					var file = write(configuration);
					var failure = assertThrows(CommandException.class,
							() -> new BulkMigrationJobConfigurationResolver().resolve(file, connection));
					assertTrue(failure.getMessage().contains("Task: items"), failure.getMessage());
				}
				configuration.getTasks().getFirst().setColumnConversions(Map.of("NAME", "value"));
				configuration.getTasks().getFirst().setKeyColumns(List.of("NAME"));
				var upsertKey = write(configuration);
				assertTrue(assertThrows(CommandException.class,
						() -> new BulkMigrationJobConfigurationResolver().resolve(upsertKey, connection)).getMessage()
						.contains("UPSERT key column ITEMS.NAME"));
				configuration.getTasks().getFirst().setKeyColumns(List.of());
				configuration.getTasks().getFirst().setKeysetColumns(List.of("NAME"));
				var keyset = write(configuration);
				assertTrue(assertThrows(CommandException.class,
						() -> new BulkMigrationJobConfigurationResolver().resolve(keyset, connection)).getMessage()
						.contains("keyset column ITEMS.NAME"));
			}
		}
	}

	private BulkMigrationJobConfiguration configuration(Map<String, String> conversions) throws Exception {
		var schema = new Schema("PUBLIC");
		var table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR).setLength(30));
		table.getColumns().add(new Column("PRICE").setDataType(DataType.INT));
		table.setPrimaryKey("PK_ITEMS", table.getColumns().get("ID"));
		schema.getTables().add(table);
		schema.writeXml(directory.resolve("schema.xml").toFile());
		var configuration = new BulkMigrationJobConfiguration();
		configuration.setSchemaFile("schema.xml");
		var task = new BulkMigrationJobConfiguration.Task();
		task.setId("items");
		task.setTable("PUBLIC.ITEMS");
		task.setChunkSize(2);
		task.setCheckpointMode(BulkMigrationCheckpointMode.FILE);
		task.setCheckpointDirectory("checkpoints");
		task.setTargetFingerprint("target-v1");
		task.setColumnConversions(conversions);
		configuration.setTasks(List.of(task));
		configuration.setVerification(new BulkMigrationJobConfiguration.Verification());
		return configuration;
	}

	private File write(BulkMigrationJobConfiguration configuration) throws Exception {
		var file = directory.resolve("job-" + UUID.randomUUID() + ".yaml").toFile();
		new YamlConverter().writeJsonValue(file, configuration);
		return file;
	}

	private static ExecuteBulkMigrationJobCommand command(File file, DataSource source, DataSource target) {
		var command = new ExecuteBulkMigrationJobCommand();
		command.setConfigurationFile(file);
		command.setSourceDataSource(source);
		command.setDataSource(target);
		command.setCloseDataSource(false);
		return command;
	}

	private static String messages(Throwable failure) {
		var text = new StringBuilder();
		for (Throwable current = failure; current != null; current = current.getCause())
			text.append(current.getMessage()).append('\n');
		return text.toString();
	}

	private static List<String> rows(DataSource target) throws Exception {
		var result = new ArrayList<String>();
		try (var c = target.getConnection();
				var s = c.createStatement();
				var r = s.executeQuery("SELECT ID, NAME, PRICE FROM PUBLIC.ITEMS ORDER BY ID")) {
			while (r.next())
				result.add(r.getInt(1) + "|" + r.getString(2) + "|" + r.getString(3));
		}
		return result;
	}

	private static void sql(DataSource dataSource, String... commands) throws Exception {
		try (var c = dataSource.getConnection(); Statement s = c.createStatement()) {
			for (var command : commands)
				s.execute(command);
		}
	}

	private static HikariDataSource dataSource() {
		var config = new HikariConfig();
		config.setJdbcUrl("jdbc:hsqldb:mem:bulk_conversions_" + UUID.randomUUID());
		config.setUsername("SA");
		config.setPassword("");
		return new HikariDataSource(config);
	}
}
