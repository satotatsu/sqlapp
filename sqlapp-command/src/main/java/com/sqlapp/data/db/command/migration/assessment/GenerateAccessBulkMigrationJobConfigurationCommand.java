/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.assessment;

import java.io.File;
import java.nio.file.Path;
import java.util.Locale;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfiguration;
import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.assessment.ResolvedMigrationTargetMapping;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.bulk.BulkMigrationMode;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpointMode;
import com.sqlapp.jdbc.bulk.BulkMigrationJobLeaseMode;
import com.sqlapp.util.JsonConverter;
import com.sqlapp.util.YamlConverter;

import lombok.Getter;
import lombok.Setter;

/** Generates a safe initial-load job from a reviewed Access assessment. */
@Getter
@Setter
public class GenerateAccessBulkMigrationJobConfigurationCommand extends AbstractCommand {
	private File assessmentReportFile;
	private File schemaFile;
	private File outputFile;
	private String jobId;
	private int chunkSize = 10_000;
	private boolean resume = true;
	private boolean verification = true;
	private String verificationReportFile;
	private boolean operationalReport = true;
	private String operationalReportFile;
	private BulkMigrationCheckpointMode checkpointMode = BulkMigrationCheckpointMode.DATABASE;
	private String checkpointDirectory;
	private String leaseOwnerId;
	private long leaseDurationSeconds = 300;
	private String expectedAssessmentReportFingerprint;
	private File ddlVerificationReportFile;
	private String expectedDdlVerificationReportFingerprint;

	@Override
	protected void doRun() {
		validateProperties();
		try {
			if (expectedAssessmentReportFingerprint != null && !expectedAssessmentReportFingerprint.isBlank()) {
				if (!expectedAssessmentReportFingerprint.matches("sha256:[0-9a-f]{64}")) {
					throw new CommandException("expectedAssessmentReportFingerprint must be a lowercase SHA-256 value");
				}
				if (!expectedAssessmentReportFingerprint.equals(AssessMigrationCommand.fingerprint(assessmentReportFile))) {
					throw new CommandException("assessmentReportFile fingerprint does not match expectedAssessmentReportFingerprint");
				}
			}
			final var report = new JsonConverter().fromJsonString(assessmentReportFile,
					AssessDatabaseMigrationCommand.Report.class);
			if (report == null || report.targetMapping() == null || report.formatVersion() != 3) {
				throw new CommandException("assessmentReportFile must be a mapped formatVersion=3 assessment");
			}
			if (!"access".equalsIgnoreCase(report.sourceProduct())) {
				throw new CommandException("assessmentReportFile sourceProduct must be access");
			}
			if (report.sourceFingerprint() != null && !report.sourceFingerprint().isBlank()
					&& !report.sourceFingerprint().equals(AssessMigrationCommand.fingerprint(schemaFile))) {
				throw new CommandException("schemaFile fingerprint does not match assessmentReportFile sourceFingerprint");
			}
			if (report.assessment() == null || report.assessment().hasBlockers()
					|| !"REVIEW_REQUIRED".equalsIgnoreCase(report.status())) {
				throw new CommandException("assessmentReportFile must have REVIEW_REQUIRED status and no blockers");
			}
			requireZero(report, "unmappedTables");
			requireZero(report, "unmappedColumnsInMappedTables");
				requireZero(report, "unresolvedAutoNumberStrategies");
			if (ddlVerificationReportFile != null) {
				validateDdlVerificationReport(report);
			}
			final DbCommonObject<?> schema = SchemaUtils.readXml(schemaFile);
			final var configuration = configuration(report, SchemaUtils.toTables(schema),
					AssessMigrationCommand.fingerprint(assessmentReportFile),
					ddlVerificationReportFile == null ? null
							: AssessMigrationCommand.fingerprint(ddlVerificationReportFile));
			AtomicMigrationFile.write(outputFile.toPath().toAbsolutePath().normalize(),
					temporary -> new YamlConverter().writeJsonValue(temporary.toFile(), configuration));
		} catch (final CommandException e) {
			throw e;
		} catch (final Exception e) {
			throw new CommandException("Could not generate Access bulk migration job configuration: " + e.getMessage(), e);
		}
		info("Access bulk migration job configuration generated: ", outputFile.getAbsolutePath());
	}

	private BulkMigrationJobConfiguration configuration(final AssessDatabaseMigrationCommand.Report report,
			final java.util.List<Table> sourceTables, final String assessmentReportFingerprint,
			final String ddlVerificationReportFingerprint) {
		final ResolvedMigrationTargetMapping mapping = report.targetMapping();
		validateTargetIdentity(report, mapping);
		final String mappingFingerprint = mappingFingerprint(report, mapping);
		final var configuration = new BulkMigrationJobConfiguration();
		final String effectiveJobId = jobId == null || jobId.isBlank()
				? "access-to-" + mapping.targetDatabase().toLowerCase(Locale.ROOT) : jobId;
		configuration.setJobId(effectiveJobId);
		configuration.setSchemaFile(relativePath(outputFile, schemaFile));
		configuration.setSchemaFingerprint(report.sourceFingerprint());
		final var provenance = new BulkMigrationJobConfiguration.Provenance();
		provenance.setAssessmentReportFingerprint(assessmentReportFingerprint);
		provenance.setDdlVerificationReportFingerprint(ddlVerificationReportFingerprint);
		configuration.setProvenance(provenance);
		final var taskIds = new java.util.HashSet<String>();
		for (final var table : mapping.tables()) {
			validateDirectLoad(table);
			final Table sourceTable = validateSourceSchema(table, sourceTables);
			final var task = new BulkMigrationJobConfiguration.Task();
			final String sourceTableName = qualified(table.sourceTable().catalog(), table.sourceTable().schema(),
					table.sourceTable().name());
			final String taskId = "access:" + sourceTableName;
			if (taskId.length() > com.sqlapp.jdbc.bulk.BulkMigrationCheckpoint.ID_MAX_LENGTH) {
				throw new CommandException("Mapped Access table name is too long for a checkpoint ID: " + sourceTableName);
			}
			if (!taskIds.add(taskId.toLowerCase(Locale.ROOT))) {
				throw new CommandException("Mapped Access table produces a duplicate task ID: " + sourceTableName);
			}
			task.setId(taskId);
			final String migrationId = effectiveJobId + ":" + sourceTableName;
			if (migrationId.length() > com.sqlapp.jdbc.bulk.BulkMigrationCheckpoint.ID_MAX_LENGTH) {
				throw new CommandException("jobId and mapped Access table name are too long for a checkpoint ID: "
						+ sourceTableName);
			}
			task.setMigrationId(migrationId);
			task.setTable(sourceTableName);
			task.setKeysetColumns(sourceTable.getPrimaryKeyConstraint().getColumns().stream()
					.map(reference -> reference.getColumn() == null ? reference.getName() : reference.getColumn().getName())
					.toList());
			task.setVerificationColumns(sourceTable.getColumns().stream()
					.filter(column -> !column.isHidden())
					.filter(column -> column.getFormula() == null || column.getFormula().isEmpty())
					.map(column -> column.getName()).toList());
			if (task.getVerificationColumns().isEmpty()) {
				throw new CommandException("Mapped Access table has no columns available for loading and verification: "
						+ sourceTableName);
			}
			task.setMode(BulkMigrationMode.INSERT);
			task.setChunkSize(chunkSize);
			task.setResume(resume);
			task.setCheckpointMode(checkpointMode);
			if (checkpointMode == BulkMigrationCheckpointMode.FILE) {
				task.setCheckpointDirectory(checkpointDirectory == null || checkpointDirectory.isBlank()
						? defaultReportFile(outputFile, "-checkpoints") : checkpointDirectory);
			}
			task.setSourceFingerprint(report.sourceFingerprint());
			task.setTargetFingerprint(mappingFingerprint);
			task.getBulk().setKeepIdentity(sourceTable.getColumns().stream().anyMatch(column -> column.isIdentity()));
			configuration.getTasks().add(task);
		}
		if (configuration.getTasks().isEmpty()) {
			throw new CommandException("assessmentReportFile targetMapping must contain at least one table");
		}
		if (verification) {
			final var verificationConfiguration = new BulkMigrationJobConfiguration.Verification();
			verificationConfiguration.setEnabled(true);
			verificationConfiguration.setChunkSize(chunkSize);
			verificationConfiguration.setFailOnMismatch(true);
			verificationConfiguration.setTargetFile(verificationReportFile == null || verificationReportFile.isBlank()
					? defaultVerificationReportFile(outputFile) : verificationReportFile);
			configuration.setVerification(verificationConfiguration);
		}
		if (operationalReport) {
			final var reportConfiguration = new BulkMigrationJobConfiguration.Report();
			reportConfiguration.setTargetFile(operationalReportFile == null || operationalReportFile.isBlank()
					? defaultReportFile(outputFile, "-operations.json") : operationalReportFile);
			configuration.setReport(reportConfiguration);
		}
		if (leaseOwnerId != null && !leaseOwnerId.isBlank()) {
			final var lease = new BulkMigrationJobConfiguration.Lease();
			lease.setMode(BulkMigrationJobLeaseMode.DATABASE);
			lease.setOwnerId(leaseOwnerId);
			lease.setDurationSeconds(leaseDurationSeconds);
			configuration.setLease(lease);
		}
		return configuration;
	}

	private static Table validateSourceSchema(final ResolvedMigrationTargetMapping.TableMapping mapping,
			final java.util.List<Table> sourceTables) {
		final var id = mapping.sourceTable();
		final var matches = sourceTables.stream().filter(table -> same(id.name(), table.getName())
				&& same(id.schema(), table.getSchemaName()) && same(id.catalog(), table.getCatalogName())).toList();
		if (matches.size() != 1) {
			throw new CommandException(matches.isEmpty() ? "Mapped Access table was not found in schemaFile: " + id.name()
					: "Mapped Access table is ambiguous in schemaFile: " + id.name());
		}
		final Table table = matches.getFirst();
		if (table.getPrimaryKeyConstraint() == null || table.getPrimaryKeyConstraint().getColumns().isEmpty()) {
			throw new CommandException("Mapped Access table requires a primary key for resumable loading: " + id.name());
		}
		for (final var reference : table.getPrimaryKeyConstraint().getColumns()) {
			final var primaryKeyColumn = reference.getColumn() == null ? table.getColumns().get(reference.getName())
					: reference.getColumn();
			if (primaryKeyColumn == null) {
				throw new CommandException("Mapped Access primary key column was not found in schemaFile: "
						+ id.name() + "." + reference.getName());
			}
			if (!primaryKeyColumn.isNotNull()) {
				throw new CommandException("Mapped Access primary key column must be NOT NULL for resumable loading: "
						+ id.name() + "." + primaryKeyColumn.getName());
			}
		}
		if (mapping.columns().size() != table.getColumns().size()) {
			throw new CommandException("Mapped Access table must include every Schema XML column: " + id.name());
		}
		final var names = new java.util.HashSet<String>();
		for (final var column : mapping.columns()) {
			final String name = column.sourceColumn().name();
			final var source = table.getColumns().get(name);
			if (source == null || !names.add(source.getName().toLowerCase(Locale.ROOT))) {
				throw new CommandException("Mapped Access column is missing or duplicated in schemaFile: "
						+ id.name() + "." + name);
			}
		}
		return table;
	}

	private static void validateDirectLoad(final ResolvedMigrationTargetMapping.TableMapping table) {
		final var source = table.sourceTable();
		if (!same(source.name(), table.targetTable()) || !same(source.schema(), table.targetSchema())) {
			throw new CommandException("Existing bulk migration requires unchanged table and schema names: "
					+ source.name());
		}
		for (final var column : table.columns()) {
			if (!same(column.sourceColumn().name(), column.targetColumn())) {
				throw new CommandException("Existing bulk migration requires unchanged column names: "
						+ source.name() + "." + column.sourceColumn().name());
			}
			if (column.conversion() != null && !column.conversion().isBlank()) {
				throw new CommandException("Existing bulk migration cannot apply mapping conversion: "
						+ source.name() + "." + column.sourceColumn().name());
			}
		}
	}

	private void validateProperties() {
		if (assessmentReportFile == null || !assessmentReportFile.isFile()) {
			throw new CommandException("assessmentReportFile must be an existing assessment JSON file");
		}
		if (schemaFile == null || !schemaFile.isFile()) {
			throw new CommandException("schemaFile must be an existing Schema XML file");
		}
		if (outputFile == null || outputFile.isDirectory()) {
			throw new CommandException("outputFile must be a YAML file path");
		}
		if (ddlVerificationReportFile != null && !ddlVerificationReportFile.isFile()) {
			throw new CommandException("ddlVerificationReportFile must be an existing verification JSON file");
		}
		if (expectedDdlVerificationReportFingerprint != null
				&& !expectedDdlVerificationReportFingerprint.isBlank() && ddlVerificationReportFile == null) {
			throw new CommandException("expectedDdlVerificationReportFingerprint requires ddlVerificationReportFile");
		}
		if (chunkSize <= 0) { throw new CommandException("chunkSize must be greater than zero"); }
		if (checkpointMode == null) { throw new CommandException("checkpointMode must not be null"); }
		if (checkpointMode == BulkMigrationCheckpointMode.CUSTOM) {
			throw new CommandException("checkpointMode=CUSTOM is not available in generated YAML");
		}
		if (checkpointMode != BulkMigrationCheckpointMode.FILE
				&& checkpointDirectory != null && !checkpointDirectory.isBlank()) {
			throw new CommandException("checkpointDirectory requires checkpointMode=FILE");
		}
		if (leaseOwnerId != null && !leaseOwnerId.isBlank()) {
			if (leaseOwnerId.length() > com.sqlapp.jdbc.bulk.BulkMigrationJobLease.ID_MAX_LENGTH) {
				throw new CommandException("leaseOwnerId must not exceed "
						+ com.sqlapp.jdbc.bulk.BulkMigrationJobLease.ID_MAX_LENGTH + " characters");
			}
			if (leaseDurationSeconds <= 0) {
				throw new CommandException("leaseDurationSeconds must be greater than zero");
			}
		}
		final Path output = outputFile.toPath().toAbsolutePath().normalize();
		if (output.equals(assessmentReportFile.toPath().toAbsolutePath().normalize())
				|| output.equals(schemaFile.toPath().toAbsolutePath().normalize())
				|| ddlVerificationReportFile != null
						&& output.equals(ddlVerificationReportFile.toPath().toAbsolutePath().normalize())) {
			throw new CommandException("outputFile must not overwrite an input file");
		}
	}

	private void validateDdlVerificationReport(final AssessDatabaseMigrationCommand.Report report) throws Exception {
		if (expectedDdlVerificationReportFingerprint != null
				&& !expectedDdlVerificationReportFingerprint.isBlank()) {
			if (!expectedDdlVerificationReportFingerprint.matches("sha256:[0-9a-f]{64}")) {
				throw new CommandException(
						"expectedDdlVerificationReportFingerprint must be a lowercase SHA-256 value");
			}
			if (!expectedDdlVerificationReportFingerprint
					.equals(AssessMigrationCommand.fingerprint(ddlVerificationReportFile))) {
				throw new CommandException(
						"ddlVerificationReportFile fingerprint does not match expectedDdlVerificationReportFingerprint");
			}
		}
		final var verification = new JsonConverter().fromJsonString(ddlVerificationReportFile,
				VerifyDatabaseMigrationDdlPhasesCommand.VerificationReport.class);
		if (verification == null || verification.formatVersion() != 2
				|| !"VERIFIED".equals(verification.status())) {
			throw new CommandException("ddlVerificationReportFile must be a VERIFIED formatVersion=2 report");
		}
		if (verification.verificationPolicies() == null
				|| !verification.verificationPolicies().contains("DEPLOYMENT_READY")) {
			throw new CommandException("ddlVerificationReportFile must include the DEPLOYMENT_READY policy");
		}
		final String assessmentFingerprint = AssessMigrationCommand.fingerprint(assessmentReportFile);
		if (!assessmentFingerprint.equals(verification.assessmentReportFingerprint())) {
			throw new CommandException("ddlVerificationReportFile does not match assessmentReportFile");
		}
		if (!java.util.Objects.equals(report.sourceFingerprint(), verification.sourceFingerprint())
				|| !java.util.Objects.equals(report.mappingFingerprint(), verification.mappingFingerprint())
				|| !same(report.targetProduct(), verification.targetDatabase())
				|| !same(report.targetVersion(), verification.targetVersion())) {
			throw new CommandException("ddlVerificationReportFile provenance does not match the assessment");
		}
	}

	private static boolean same(final String source, final String target) {
		return source == null || source.isBlank() ? target == null || target.isBlank()
				: target != null && source.equalsIgnoreCase(target);
	}

	private static void requireZero(final AssessDatabaseMigrationCommand.Report report, final String type) {
		final var value = report.assessment().inventory().stream().filter(item -> type.equals(item.type())).findFirst()
				.orElseThrow(() -> new CommandException("assessmentReportFile must contain " + type + " inventory"));
		if (value.count() != 0) {
			throw new CommandException("assessmentReportFile must have " + type + "=0 but was " + value.count());
		}
	}

	private static String mappingFingerprint(final AssessDatabaseMigrationCommand.Report report,
			final ResolvedMigrationTargetMapping mapping) {
		final String reportValue = report.mappingFingerprint();
		final String mappingValue = mapping.mappingFingerprint();
		if (reportValue != null && !reportValue.isBlank() && mappingValue != null && !mappingValue.isBlank()
				&& !reportValue.equals(mappingValue)) {
			throw new CommandException("assessmentReportFile mapping fingerprints do not match");
		}
		return reportValue == null || reportValue.isBlank() ? mappingValue : reportValue;
	}

	private static void validateTargetIdentity(final AssessDatabaseMigrationCommand.Report report,
			final ResolvedMigrationTargetMapping mapping) {
		if (report.targetProduct() == null || report.targetProduct().isBlank()
				|| mapping.targetDatabase() == null || mapping.targetDatabase().isBlank()
				|| !report.targetProduct().equalsIgnoreCase(mapping.targetDatabase())) {
			throw new CommandException("assessmentReportFile target product does not match targetMapping database");
		}
		if (report.targetVersion() == null || report.targetVersion().isBlank()
				|| mapping.targetVersion() == null || mapping.targetVersion().isBlank()
				|| !report.targetVersion().equalsIgnoreCase(mapping.targetVersion())) {
			throw new CommandException("assessmentReportFile target version does not match targetMapping version");
		}
		if (report.migrationMethod() != com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.Method.LOGICAL_MIGRATION) {
			throw new CommandException("assessmentReportFile migrationMethod must be LOGICAL_MIGRATION");
		}
	}

	private static String qualified(final String catalog, final String schema, final String table) {
		return java.util.stream.Stream.of(catalog, schema, table).filter(value -> value != null && !value.isBlank())
				.collect(java.util.stream.Collectors.joining("."));
	}

	private static String relativePath(final File output, final File input) {
		try {
			return output.getAbsoluteFile().getParentFile().toPath().toAbsolutePath().normalize()
					.relativize(input.toPath().toAbsolutePath().normalize()).toString().replace('\\', '/');
		} catch (final IllegalArgumentException e) {
			return input.getAbsolutePath();
		}
	}

	private static String defaultVerificationReportFile(final File output) {
		return defaultReportFile(output, "-verification.json");
	}

	private static String defaultReportFile(final File output, final String suffix) {
		final String name = output.getName();
		final int dot = name.lastIndexOf('.');
		return (dot <= 0 ? name : name.substring(0, dot)) + suffix;
	}
}
