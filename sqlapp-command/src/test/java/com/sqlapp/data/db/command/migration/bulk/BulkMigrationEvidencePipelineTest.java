/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
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

/** Exercises the complete approval, execution, verification and audit chain. */
class BulkMigrationEvidencePipelineTest extends AbstractDbCommandTest {
	@TempDir
	Path directory;

	@Test
	void runsAndRevalidatesTheCompleteEvidencePipeline() throws Exception {
		try (var source = dataSource("bulk_evidence_pipeline_source");
				var target = dataSource("bulk_evidence_pipeline_target")) {
			executeSql(source, "CREATE TABLE PUBLIC.ACCESS_CUSTOMERS "
					+ "(ACCESS_ID INT NOT NULL PRIMARY KEY, CUSTOMER_NAME VARCHAR(30))");
			executeSql(source, "INSERT INTO PUBLIC.ACCESS_CUSTOMERS VALUES (1, 'one'), (2, 'two')");
			executeSql(target, "CREATE TABLE PUBLIC.CUSTOMERS "
					+ "(CUSTOMER_ID INT NOT NULL PRIMARY KEY, CUSTOMER_NAME VARCHAR(30))");

			final File schemaFile = directory.resolve("access-schema.xml").toFile();
			accessSchema().writeXml(schemaFile);
			final File assessment = write("access-assessment.json", "{\"status\":\"REVIEW_REQUIRED\"}");
			final File ddlVerification = write("oracle-ddl-verification.json", "{\"status\":\"VERIFIED\"}");
			final File job = directory.resolve("access-to-oracle-load.yaml").toFile();
			new YamlConverter().writeJsonValue(job,
					configuration(schemaFile, assessment, ddlVerification));
			final String configurationFingerprint = fingerprint(job);

			final File targetValidation = directory.resolve("target-validation.json").toFile();
			final var validate = new ValidateBulkMigrationTargetCommand();
			validate.setSourceDataSource(source);
			validate.setDataSource(target);
			validate.setCloseDataSource(false);
			validate.setConfigurationFile(job);
			validate.setAssessmentReportFile(assessment);
			validate.setDdlVerificationReportFile(ddlVerification);
			validate.setExpectedConfigurationFingerprint(configurationFingerprint);
			validate.setTargetEnvironmentId("production-oracle");
			validate.setReportFile(targetValidation);
			assertDoesNotThrow(validate::run);
			assertEquals(0, countRows(target, "PUBLIC.CUSTOMERS"));

			final var execute = new ExecuteBulkMigrationJobCommand();
			execute.setSourceDataSource(source);
			execute.setDataSource(target);
			execute.setCloseDataSource(false);
			execute.setConfigurationFile(job);
			execute.setAssessmentReportFile(assessment);
			execute.setDdlVerificationReportFile(ddlVerification);
			execute.setExpectedConfigurationFingerprint(configurationFingerprint);
			execute.setTargetValidationReportFile(targetValidation);
			execute.setExpectedTargetValidationReportFingerprint(fingerprint(targetValidation));
			execute.setMaxTargetValidationAgeSeconds(300L);
			execute.setTargetEnvironmentId("production-oracle");
			assertDoesNotThrow(execute::run);
			assertEquals(2, countRows(target, "PUBLIC.CUSTOMERS"));
			assertEquals(true, execute.getVerificationResult().isMatch());

			final File operations = directory.resolve("access-load-operations.json").toFile();
			final File verification = directory.resolve("access-load-verification.json").toFile();
			final File evidence = directory.resolve("access-load-evidence.json").toFile();
			final var audit = new VerifyBulkMigrationEvidenceCommand();
			audit.setOperationalReportFile(operations);
			audit.setVerificationReportFile(verification);
			audit.setConfigurationFile(job);
			audit.setAssessmentReportFile(assessment);
			audit.setDdlVerificationReportFile(ddlVerification);
			audit.setTargetValidationReportFile(targetValidation);
			audit.setExpectedTargetEnvironmentId("production-oracle");
			audit.setOutputFile(evidence);
			assertDoesNotThrow(audit::run);

			final var revalidate = new VerifyBulkMigrationEvidenceReportCommand();
			revalidate.setEvidenceReportFile(evidence);
			revalidate.setOperationalReportFile(operations);
			revalidate.setVerificationReportFile(verification);
			revalidate.setConfigurationFile(job);
			revalidate.setAssessmentReportFile(assessment);
			revalidate.setDdlVerificationReportFile(ddlVerification);
			revalidate.setTargetValidationReportFile(targetValidation);
			revalidate.setExpectedTargetEnvironmentId("production-oracle");
			revalidate.setExpectedEvidenceReportFingerprint(fingerprint(evidence));
			revalidate.setExpectedPlanFingerprint(validate.getResult().planFingerprint());
			revalidate.setExpectedConfigurationFingerprint(configurationFingerprint);
			revalidate.setMaxEvidenceAgeSeconds(300L);
			assertDoesNotThrow(revalidate::run);

			Files.writeString(targetValidation.toPath(), "{\"changed\":true}");
			assertThrows(CommandException.class, revalidate::run);
		}
	}

	private BulkMigrationJobConfiguration configuration(final File schemaFile, final File assessment,
			final File ddlVerification) {
		final var configuration = new BulkMigrationJobConfiguration();
		configuration.setJobId("access-to-oracle-initial-load");
		configuration.setSchemaFile(schemaFile.getName());
		final var provenance = new BulkMigrationJobConfiguration.Provenance();
		provenance.setAssessmentReportFingerprint(fingerprint(assessment));
		provenance.setDdlVerificationReportFingerprint(fingerprint(ddlVerification));
		configuration.setProvenance(provenance);
		final var task = new BulkMigrationJobConfiguration.Task();
		task.setId("access:PUBLIC.ACCESS_CUSTOMERS");
		task.setTable("PUBLIC.ACCESS_CUSTOMERS");
		task.setTargetTable("PUBLIC.CUSTOMERS");
		task.setColumnMappings(Map.of("ACCESS_ID", "CUSTOMER_ID"));
		task.setKeysetColumns(List.of("ACCESS_ID"));
		task.setMode(BulkMigrationMode.UPSERT);
		task.setResume(false);
		task.setRequireEmptyTarget(true);
		configuration.setTasks(List.of(task));
		final var report = new BulkMigrationJobConfiguration.Report();
		report.setTargetFile("access-load-operations.json");
		configuration.setReport(report);
		final var verification = new BulkMigrationJobConfiguration.Verification();
		verification.setTargetFile("access-load-verification.json");
		configuration.setVerification(verification);
		return configuration;
	}

	private static Schema accessSchema() {
		final var schema = new Schema("PUBLIC");
		final var table = new Table("ACCESS_CUSTOMERS");
		table.getColumns().add(new Column("ACCESS_ID").setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add(new Column("CUSTOMER_NAME").setDataType(DataType.VARCHAR).setLength(30));
		table.setPrimaryKey("PK_ACCESS_CUSTOMERS", table.getColumns().get("ACCESS_ID"));
		schema.getTables().add(table);
		return schema;
	}

	private File write(final String name, final String content) throws Exception {
		return Files.writeString(directory.resolve(name), content).toFile();
	}

	private static int countRows(final HikariDataSource dataSource, final String table) throws Exception {
		try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
				var resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
			resultSet.next();
			return resultSet.getInt(1);
		}
	}

	private static String fingerprint(final File file) {
		return "sha256:" + MessageDigests.SHA256.checksumAsString(file);
	}

	private static HikariDataSource dataSource(final String name) {
		final var config = new HikariConfig();
		config.setJdbcUrl("jdbc:hsqldb:mem:" + name);
		config.setUsername("SA");
		config.setPassword("");
		return new HikariDataSource(config);
	}
}
