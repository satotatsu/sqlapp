/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.test.AbstractDbCommandTest;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.bulk.BulkMigrationMode;
import com.sqlapp.util.MessageDigests;
import com.sqlapp.util.YamlConverter;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

class ValidateBulkMigrationTargetCommandTest extends AbstractDbCommandTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void validatesMappedTargetWithoutMigratingRows() throws Exception {
		try (var source = dataSource("validate_bulk_source"); var target = dataSource("validate_bulk_target")) {
			executeSql(source, "CREATE TABLE PUBLIC.ITEMS (ID INT NOT NULL PRIMARY KEY, NAME VARCHAR(30))");
			executeSql(source, "INSERT INTO PUBLIC.ITEMS VALUES (1, 'one')");
			executeSql(target,
					"CREATE TABLE PUBLIC.CUSTOMERS (CUSTOMER_ID INT NOT NULL PRIMARY KEY, NAME VARCHAR(30))");

			final Schema schema = new Schema("PUBLIC");
			final Table table = new Table("ITEMS");
			table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
			table.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR).setLength(30));
			table.setPrimaryKey("PK_ITEMS", table.getColumns().get("ID"));
			schema.getTables().add(table);
			final File schemaFile = temporaryDirectory.resolve("schema.xml").toFile();
			schema.writeXml(schemaFile);

			final var configuration = new BulkMigrationJobConfiguration();
			configuration.setJobId("access-initial-load");
			configuration.setSchemaFile(schemaFile.getName());
			final var task = new BulkMigrationJobConfiguration.Task();
			task.setId("access:PUBLIC.ITEMS");
			task.setTable("PUBLIC.ITEMS");
			task.setTargetTable("PUBLIC.CUSTOMERS");
			task.setColumnMappings(Map.of("ID", "CUSTOMER_ID"));
			task.setKeysetColumns(List.of("ID"));
			task.setMode(BulkMigrationMode.INSERT);
			task.setResume(false);
			task.setRequireEmptyTarget(true);
			configuration.setTasks(List.of(task));
			final File assessment = temporaryDirectory.resolve("assessment.json").toFile();
			final File ddlVerification = temporaryDirectory.resolve("ddl-verification.json").toFile();
			Files.writeString(assessment.toPath(), "{\"status\":\"REVIEW_REQUIRED\"}");
			Files.writeString(ddlVerification.toPath(), "{\"status\":\"VERIFIED\"}");
			final var provenance = new BulkMigrationJobConfiguration.Provenance();
			provenance.setAssessmentReportFingerprint(
					"sha256:" + MessageDigests.SHA256.checksumAsString(assessment));
			provenance.setDdlVerificationReportFingerprint(
					"sha256:" + MessageDigests.SHA256.checksumAsString(ddlVerification));
			configuration.setProvenance(provenance);
			final File job = temporaryDirectory.resolve("job.yaml").toFile();
			new YamlConverter().writeJsonValue(job, configuration);
			final File report = temporaryDirectory.resolve("reports/target-validation.json").toFile();

			final var command = new ValidateBulkMigrationTargetCommand();
			command.setDataSource(target);
			command.setSourceDataSource(source);
			command.setCloseDataSource(false);
			command.setConfigurationFile(job);
			command.setAssessmentReportFile(assessment);
			command.setDdlVerificationReportFile(ddlVerification);
			command.setReportFile(report);
			command.setTargetEnvironmentId("production-oracle");
			command.setExpectedConfigurationFingerprint(
					"sha256:" + MessageDigests.SHA256.checksumAsString(job));
			command.setMaxApprovalArtifactFileSizeBytes(Files.size(assessment.toPath()) - 1);
			assertEquals("assessmentReportFile exceeds maxApprovalArtifactFileSizeBytes.",
					assertThrows(CommandException.class, command::run).getMessage());
			command.setMaxApprovalArtifactFileSizeBytes(Math.max(Files.size(assessment.toPath()),
					Files.size(ddlVerification.toPath())));
			command.setMaxConfigurationFileSizeBytes(Files.size(job.toPath()) - 1);
			assertEquals("Configuration file exceeds maxConfigurationFileSizeBytes.",
					assertThrows(CommandException.class, command::run).getMessage());
			command.setMaxConfigurationFileSizeBytes(Files.size(job.toPath()));
			command.setMaxSchemaFileSizeBytes(Files.size(schemaFile.toPath()) - 1);
			assertEquals("Schema XML file exceeds maxSchemaFileSizeBytes.",
					assertThrows(CommandException.class, command::run).getMessage());
			command.setMaxSchemaFileSizeBytes(Files.size(schemaFile.toPath()));
			command.run();

			assertNotNull(command.getResult());
			assertEquals(List.of("access:PUBLIC.ITEMS"), command.getResult().taskIds());
			assertEquals(command.getExpectedConfigurationFingerprint(), command.getResult().configurationFingerprint());
			assertEquals(0, countRows(target, "PUBLIC.CUSTOMERS"));
			final var saved = new BulkMigrationTargetValidationReportIO().read(report.toPath());
			assertEquals("access-initial-load", saved.jobId());
			assertEquals(command.getResult().planFingerprint(), saved.planFingerprint());
			assertEquals(List.of("access:PUBLIC.ITEMS"), saved.taskIds());
			assertEquals("production-oracle", saved.targetEnvironmentId());
			assertEquals("HSQL Database Engine", saved.databaseProductName());
			assertEquals("sha256:" + MessageDigests.SHA256.checksumAsString(report),
					command.getTargetValidationReportFingerprint());

			Files.writeString(assessment.toPath(), "{\"status\":\"CHANGED\"}");
			assertThrows(CommandException.class, command::run);
			assertNull(command.getResult());
			Files.writeString(assessment.toPath(), "{\"status\":\"REVIEW_REQUIRED\"}");

			executeSql(target, "INSERT INTO PUBLIC.CUSTOMERS VALUES (1, 'existing')");
			assertThrows(RuntimeException.class, command::run);
			assertNull(command.getResult());

			command.setExpectedConfigurationFingerprint("sha256:" + "0".repeat(64));
			assertThrows(CommandException.class, command::run);
		}
	}

	private static int countRows(final HikariDataSource dataSource, final String table) throws Exception {
		try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
				var resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
			resultSet.next();
			return resultSet.getInt(1);
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
