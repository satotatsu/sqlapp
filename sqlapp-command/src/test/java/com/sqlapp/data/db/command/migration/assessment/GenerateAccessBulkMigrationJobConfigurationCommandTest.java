/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.assessment;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfiguration;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.bulk.BulkMigrationMode;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpointMode;
import com.sqlapp.jdbc.bulk.BulkMigrationJobLeaseMode;
import com.sqlapp.util.YamlConverter;

class GenerateAccessBulkMigrationJobConfigurationCommandTest {
	@TempDir Path directory;

	@Test
	void generatesExistingBulkJobConfiguration() throws Exception {
		final Path schema = Files.writeString(directory.resolve("source.xml"), schema(true));
		final Path report = Files.writeString(directory.resolve("assessment.json"), report(schema, "CUSTOMERS", "ID", null));
		final Path output = directory.resolve("access-load.yaml");
		final var command = new GenerateAccessBulkMigrationJobConfigurationCommand();
		command.setAssessmentReportFile(report.toFile());
		command.setSchemaFile(schema.toFile());
		command.setOutputFile(output.toFile());
		command.setChunkSize(250);
		command.setLeaseOwnerId("ci-worker-1");
		assertDoesNotThrow(command::run);

		final var value = new YamlConverter().fromJsonString(output.toFile(), BulkMigrationJobConfiguration.class);
		assertTrue(value.getTasks().getFirst().isRequireEmptyTarget());
		assertEquals("access-to-sqlserver", value.getJobId());
		assertEquals("source.xml", value.getSchemaFile());
		assertEquals(AssessMigrationCommand.fingerprint(schema.toFile()), value.getSchemaFingerprint());
		assertNotNull(value.getProvenance());
		assertEquals(AssessMigrationCommand.fingerprint(report.toFile()),
				value.getProvenance().getAssessmentReportFingerprint());
		assertNull(value.getProvenance().getDdlVerificationReportFingerprint());
		assertEquals(1, value.getTasks().size());
		final var task = value.getTasks().getFirst();
		assertEquals("access:access.CUSTOMERS", task.getId());
		assertEquals("access-to-sqlserver:access.CUSTOMERS", task.getMigrationId());
		assertEquals("access.CUSTOMERS", task.getTable());
		assertEquals(java.util.List.of("ID"), task.getKeysetColumns());
		assertEquals(java.util.List.of("ID"), task.getVerificationColumns());
		assertEquals(BulkMigrationMode.INSERT, task.getMode());
		assertEquals(250, task.getChunkSize());
		assertTrue(task.isResume());
		assertTrue(task.getBulk().isKeepIdentity());
		assertEquals(AssessMigrationCommand.fingerprint(schema.toFile()), task.getSourceFingerprint());
		assertEquals("sha256:" + "2".repeat(64), task.getTargetFingerprint());
		assertNotNull(value.getVerification());
		assertTrue(value.getVerification().isEnabled());
		assertTrue(value.getVerification().isFailOnMismatch());
		assertEquals(250, value.getVerification().getChunkSize());
		assertEquals("access-load-verification.json", value.getVerification().getTargetFile());
		assertNotNull(value.getReport());
		assertEquals("access-load-operations.json", value.getReport().getTargetFile());
		assertNotNull(value.getLease());
		assertEquals(BulkMigrationJobLeaseMode.DATABASE, value.getLease().getMode());
		assertEquals("ci-worker-1", value.getLease().getOwnerId());
		assertEquals(300, value.getLease().getDurationSeconds());
	}

	@Test
	void canDisablePostLoadVerification() throws Exception {
		final Path schema = Files.writeString(directory.resolve("source.xml"), schema(true));
		final Path report = Files.writeString(directory.resolve("assessment.json"), report(schema, "CUSTOMERS", "ID", null));
		final Path output = directory.resolve("job.yaml");
		final var command = command(schema, report, output);
		command.setVerification(false);
		command.setOperationalReport(false);
		command.setCheckpointMode(BulkMigrationCheckpointMode.FILE);
		command.run();
		final var value = new YamlConverter().fromJsonString(output.toFile(), BulkMigrationJobConfiguration.class);
		assertNull(value.getVerification());
		assertNull(value.getReport());
		assertEquals(BulkMigrationCheckpointMode.FILE, value.getTasks().getFirst().getCheckpointMode());
		assertEquals("job-checkpoints", value.getTasks().getFirst().getCheckpointDirectory());
	}

	@Test
	void preservesAccessAutoNumberWhenTargetUsesANonIdentityStrategy() throws Exception {
		final Path schema = Files.writeString(directory.resolve("source.xml"), schema(true));
		final Path report = Files.writeString(directory.resolve("assessment.json"),
				report(schema, "CUSTOMERS", "ID", null).replace("\"identity\":true", "\"identity\":false"));
		final Path output = directory.resolve("job.yaml");
		command(schema, report, output).run();
		final var value = new YamlConverter().fromJsonString(output.toFile(), BulkMigrationJobConfiguration.class);
		assertTrue(value.getTasks().getFirst().getBulk().isKeepIdentity());
	}

	@Test
	void rejectsNullablePrimaryKeyBeforeExecution() throws Exception {
		final Path schema = Files.writeString(directory.resolve("source.xml"),
				schema(true).replace(" notNull=\"true\"", " notNull=\"false\""));
		final Path report = Files.writeString(directory.resolve("assessment.json"),
				report(schema, "CUSTOMERS", "ID", null));
		final var command = command(schema, report, directory.resolve("job.yaml"));
		assertTrue(assertThrows(CommandException.class, command::run).getMessage().contains("must be NOT NULL"));
	}

	@Test
	void rejectsUnsupportedCheckpointConfiguration() throws Exception {
		final Path schema = Files.writeString(directory.resolve("source.xml"), schema(true));
		final Path report = Files.writeString(directory.resolve("assessment.json"),
				report(schema, "CUSTOMERS", "ID", null));
		final var command = command(schema, report, directory.resolve("job.yaml"));
		command.setCheckpointMode(BulkMigrationCheckpointMode.CUSTOM);
		assertTrue(assertThrows(CommandException.class, command::run).getMessage().contains("CUSTOM"));

		command.setCheckpointMode(BulkMigrationCheckpointMode.DATABASE);
		command.setCheckpointDirectory("state");
		assertTrue(assertThrows(CommandException.class, command::run).getMessage().contains("requires checkpointMode=FILE"));
	}

	@Test
	void rejectsInvalidLeaseConfiguration() throws Exception {
		final Path schema = Files.writeString(directory.resolve("source.xml"), schema(true));
		final Path report = Files.writeString(directory.resolve("assessment.json"),
				report(schema, "CUSTOMERS", "ID", null));
		final var command = command(schema, report, directory.resolve("job.yaml"));
		command.setLeaseOwnerId("ci-worker");
		command.setLeaseDurationSeconds(0);
		assertTrue(assertThrows(CommandException.class, command::run).getMessage().contains("leaseDurationSeconds"));
	}

	@Test
	void optionallyRequiresTheApprovedAssessmentReport() throws Exception {
		final Path schema = Files.writeString(directory.resolve("source.xml"), schema(true));
		final Path report = Files.writeString(directory.resolve("assessment.json"),
				report(schema, "CUSTOMERS", "ID", null));
		final var command = command(schema, report, directory.resolve("job.yaml"));
		command.setExpectedAssessmentReportFingerprint("sha256:" + "0".repeat(64));
		assertTrue(assertThrows(CommandException.class, command::run).getMessage()
				.contains("expectedAssessmentReportFingerprint"));

		command.setExpectedAssessmentReportFingerprint(AssessMigrationCommand.fingerprint(report.toFile()));
		assertDoesNotThrow(command::run);
	}

	@Test
	void optionallyRequiresMatchingDeploymentReadyDdlEvidence() throws Exception {
		final Path schema = Files.writeString(directory.resolve("source.xml"), schema(true));
		final Path report = Files.writeString(directory.resolve("assessment.json"),
				report(schema, "CUSTOMERS", "ID", null));
		final String assessmentFingerprint = AssessMigrationCommand.fingerprint(report.toFile());
		final String sourceFingerprint = AssessMigrationCommand.fingerprint(schema.toFile());
		final Path ddlVerification = directory.resolve("ddl-verification.json");
		Files.writeString(ddlVerification, ddlVerification(assessmentFingerprint, sourceFingerprint, "DEPLOYMENT_READY"));
		final var command = command(schema, report, directory.resolve("job.yaml"));
		command.setDdlVerificationReportFile(ddlVerification.toFile());
		command.setExpectedDdlVerificationReportFingerprint(
				AssessMigrationCommand.fingerprint(ddlVerification.toFile()));
		assertDoesNotThrow(command::run);
		final var value = new YamlConverter().fromJsonString(directory.resolve("job.yaml").toFile(),
				BulkMigrationJobConfiguration.class);
		assertEquals(AssessMigrationCommand.fingerprint(ddlVerification.toFile()),
				value.getProvenance().getDdlVerificationReportFingerprint());

		Files.writeString(ddlVerification, ddlVerification(assessmentFingerprint, sourceFingerprint, "DATA_SCAN"));
		command.setExpectedDdlVerificationReportFingerprint(
				AssessMigrationCommand.fingerprint(ddlVerification.toFile()));
		assertTrue(assertThrows(CommandException.class, command::run).getMessage().contains("DEPLOYMENT_READY"));
	}

	@Test
	void rejectsChangedDdlVerificationEvidence() throws Exception {
		final Path schema = Files.writeString(directory.resolve("source.xml"), schema(true));
		final Path report = Files.writeString(directory.resolve("assessment.json"),
				report(schema, "CUSTOMERS", "ID", null));
		final Path ddlVerification = Files.writeString(directory.resolve("ddl-verification.json"), "{}");
		final var command = command(schema, report, directory.resolve("job.yaml"));
		command.setDdlVerificationReportFile(ddlVerification.toFile());
		command.setExpectedDdlVerificationReportFingerprint("sha256:" + "0".repeat(64));
		assertTrue(assertThrows(CommandException.class, command::run).getMessage()
				.contains("expectedDdlVerificationReportFingerprint"));

		command.setExpectedDdlVerificationReportFingerprint("SHA256:" + "0".repeat(64));
		assertTrue(assertThrows(CommandException.class, command::run).getMessage()
				.contains("lowercase SHA-256"));

		command.setDdlVerificationReportFile(null);
		command.setExpectedDdlVerificationReportFingerprint("sha256:" + "0".repeat(64));
		assertTrue(assertThrows(CommandException.class, command::run).getMessage()
				.contains("requires ddlVerificationReportFile"));
	}

	@Test
	void rejectsMappingsTheExistingExecutorCannotApply() throws Exception {
		final Path schema = Files.writeString(directory.resolve("source.xml"), schema(true));
		final Path report = Files.writeString(directory.resolve("assessment.json"), report(schema, "CUSTOMERS_NEW", "ID", null));
		final Path output = directory.resolve("job.yaml");
		final var command = command(schema, report, output);
		command.run();
		final var generated = new YamlConverter().fromJsonString(output.toFile(),
				BulkMigrationJobConfiguration.class);
		assertEquals("access.CUSTOMERS_NEW", generated.getTasks().getFirst().getTargetTable());

		Files.writeString(report, report(schema, "CUSTOMERS", "CUSTOMER_ID", null));
		command.run();
		final var renamedColumn = new YamlConverter().fromJsonString(output.toFile(),
				BulkMigrationJobConfiguration.class);
		assertEquals(java.util.Map.of("ID", "CUSTOMER_ID"),
				renamedColumn.getTasks().getFirst().getColumnMappings());

		Files.writeString(report, report(schema, "CUSTOMERS", "ID", "CAST(ID AS BIGINT)"));
		assertTrue(assertThrows(CommandException.class, command::run).getMessage().contains("cannot apply mapping conversion"));

		Files.writeString(report, report(schema, "CUSTOMERS", "ID", null)
				.replace("\"type\":\"unmappedTables\",\"count\":0", "\"type\":\"unmappedTables\",\"count\":1"));
		assertTrue(assertThrows(CommandException.class, command::run).getMessage().contains("unmappedTables=0"));

		Files.writeString(schema, schema(false));
		Files.writeString(report, report(schema, "CUSTOMERS", "ID", null));
		assertTrue(assertThrows(CommandException.class, command::run).getMessage().contains("requires a primary key"));
	}

	@Test
	void rejectsInconsistentMappingFingerprints() throws Exception {
		final Path schema = Files.writeString(directory.resolve("source.xml"), schema(true));
		final Path report = Files.writeString(directory.resolve("assessment.json"), report(schema, "CUSTOMERS", "ID", null)
				.replaceFirst("sha256:" + "2".repeat(64), "sha256:" + "3".repeat(64)));
		final var command = command(schema, report, directory.resolve("job.yaml"));
		assertTrue(assertThrows(CommandException.class, command::run).getMessage().contains("fingerprints do not match"));
	}

	@Test
	void rejectsSchemaFromAnotherAssessment() throws Exception {
		final Path assessedSchema = Files.writeString(directory.resolve("assessed.xml"), schema(true));
		final Path report = Files.writeString(directory.resolve("assessment.json"),
				report(assessedSchema, "CUSTOMERS", "ID", null));
		final Path otherSchema = Files.writeString(directory.resolve("other.xml"), schema(true) + "\n");
		final var command = command(otherSchema, report, directory.resolve("job.yaml"));
		assertTrue(assertThrows(CommandException.class, command::run).getMessage().contains("schemaFile fingerprint"));
	}

	@Test
	void rejectsInconsistentTargetIdentity() throws Exception {
		final Path schema = Files.writeString(directory.resolve("source.xml"), schema(true));
		final Path report = directory.resolve("assessment.json");
		final var command = command(schema, report, directory.resolve("job.yaml"));

		Files.writeString(report, report(schema, "CUSTOMERS", "ID", null)
				.replace("\"targetProduct\":\"sqlserver\"", "\"targetProduct\":\"oracle\""));
		assertTrue(assertThrows(CommandException.class, command::run).getMessage().contains("target product"));

		Files.writeString(report, report(schema, "CUSTOMERS", "ID", null)
				.replaceFirst("\"targetVersion\":\"2022\"", "\"targetVersion\":\"2019\""));
		assertTrue(assertThrows(CommandException.class, command::run).getMessage().contains("target version"));

		Files.writeString(report, report(schema, "CUSTOMERS", "ID", null)
				.replace("\"migrationMethod\":\"LOGICAL_MIGRATION\"", "\"migrationMethod\":\"DIRECT_UPGRADE\""));
		assertTrue(assertThrows(CommandException.class, command::run).getMessage().contains("migrationMethod"));
	}

	private GenerateAccessBulkMigrationJobConfigurationCommand command(final Path schema, final Path report,
			final Path output) {
		final var command = new GenerateAccessBulkMigrationJobConfigurationCommand();
		command.setAssessmentReportFile(report.toFile());
		command.setSchemaFile(schema.toFile());
		command.setOutputFile(output.toFile());
		return command;
	}

	private static String report(final String targetTable, final String targetColumn, final String conversion) {
		return """
				{"formatVersion":3,"sourceFingerprint":"sha256:%s","sourceProduct":"access",
				 "targetProduct":"sqlserver","targetVersion":"2022","migrationMethod":"LOGICAL_MIGRATION",
				 "dataScanned":true,"relationshipsCollected":true,"status":"REVIEW_REQUIRED",
				 "assessment":{"findings":[],"inventory":[
				 {"schema":"","type":"unmappedTables","count":0},
				 {"schema":"","type":"unmappedColumnsInMappedTables","count":0},
				 {"schema":"","type":"unresolvedAutoNumberStrategies","count":0}]},"mappingFingerprint":"sha256:%s",
				 "targetMapping":{"mappingFingerprint":"sha256:%s","targetDatabase":"sqlserver","targetVersion":"2022",
				 "tables":[{"sourceTable":{"schema":"access","type":"TABLE","name":"CUSTOMERS"},
				 "targetSchema":"access","targetTable":"%s","checkExpressions":[],
				 "columns":[{"sourceColumn":{"type":"COLUMN","name":"ID","table":"CUSTOMERS"},
				 "targetColumn":"%s","targetType":"bigint","nullable":false,"identity":true,"conversion":%s}]}]}}
				""".formatted("1".repeat(64), "2".repeat(64), "2".repeat(64), targetTable, targetColumn,
				conversion == null ? "null" : "\"" + conversion + "\"");
	}

	private static String report(final Path schema, final String targetTable, final String targetColumn,
			final String conversion) throws Exception {
		return report(targetTable, targetColumn, conversion).replace("sha256:" + "1".repeat(64),
				AssessMigrationCommand.fingerprint(schema.toFile()));
	}

	private static String schema(final boolean primaryKey) {
		return """
				<schema name="access">
				  <tables><table name="CUSTOMERS">
				    <columns><column name="ID" dataType="BIGINT" notNull="true" identity="true"/></columns>
				    %s
				  </table></tables>
				</schema>
				""".formatted(primaryKey
				? "<constraints><primaryKeyConstraint name=\"PK_CUSTOMERS\"><columns><column name=\"ID\"/></columns></primaryKeyConstraint></constraints>"
				: "");
	}

	private static String ddlVerification(final String assessmentFingerprint, final String sourceFingerprint,
			final String policy) {
		return """
				{"formatVersion":2,"status":"VERIFIED","manifestFingerprint":"sha256:%s",
				 "assessmentReportFingerprint":"%s","sourceFingerprint":"sha256:%s",
				 "mappingFingerprint":"sha256:%s","targetDatabase":"sqlserver","targetVersion":"2022",
				 "verificationPolicies":["%s"],"ddlFingerprints":{}}
				""".formatted("4".repeat(64), assessmentFingerprint, sourceFingerprint.substring("sha256:".length()),
					"2".repeat(64), policy);
	}
}
