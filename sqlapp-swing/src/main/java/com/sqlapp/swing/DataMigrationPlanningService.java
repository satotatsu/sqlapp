/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfigurationResolver;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationOperationalReport;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationOperationalReportBuilder;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationOperationalReportIO;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpointMode;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlanner;
import com.sqlapp.jdbc.bulk.BulkMigrationJobStatusInspector;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTask;
import com.sqlapp.jdbc.bulk.BulkMigrationTargetValidator;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationCheckpointStore;

/**
 * Resolves existing job YAML and reads target/checkpoint state without
 * migrating rows.
 */
final class DataMigrationPlanningService {
	record Request(Path configurationFile, String expectedConfigurationFingerprint) {
		Request {
			if (configurationFile == null || !Files.isRegularFile(configurationFile)
					|| !Files.isReadable(configurationFile)) {
				throw new IllegalArgumentException("Select a readable migration job YAML file.");
			}
			configurationFile = configurationFile.toAbsolutePath().normalize();
			expectedConfigurationFingerprint = expectedConfigurationFingerprint == null
					|| expectedConfigurationFingerprint.isBlank() ? null : expectedConfigurationFingerprint.trim();
			if (expectedConfigurationFingerprint != null
					&& !expectedConfigurationFingerprint.matches("sha256:[0-9a-f]{64}")) {
				throw new IllegalArgumentException(
						"Expected configuration fingerprint must be a lowercase SHA-256 value.");
			}
		}
	}

	record ExecutionSnapshot(com.sqlapp.data.db.command.migration.schema.MigrationPlan.DatabaseIdentity source,
			com.sqlapp.data.db.command.migration.schema.MigrationPlan.DatabaseIdentity target, String restriction,
			String pauseRestriction) {
		ExecutionSnapshot(com.sqlapp.data.db.command.migration.schema.MigrationPlan.DatabaseIdentity source,
				com.sqlapp.data.db.command.migration.schema.MigrationPlan.DatabaseIdentity target, String restriction) {
			this(source, target, restriction, "Pause availability has not been inspected.");
		}
	}

	record Result(Path configurationFile, BulkMigrationOperationalReport report, ReportReviewService.Review review,
			ExecutionSnapshot executionSnapshot,
			BulkMigrationJobConfigurationResolver.VerificationConfiguration verificationConfiguration) {
		Result(Path configurationFile, BulkMigrationOperationalReport report, ReportReviewService.Review review,
				ExecutionSnapshot executionSnapshot) {
			this(configurationFile, report, review, executionSnapshot, null);
		}

		Result(Path configurationFile, BulkMigrationOperationalReport report, ReportReviewService.Review review) {
			this(configurationFile, report, review, null);
		}
	}

	Result plan(final Request request, final PlanningJdbc.Spec source, final char[] sourcePassword,
			final PlanningJdbc.Spec target, final char[] targetPassword) {
		Objects.requireNonNull(sourcePassword, "sourcePassword");
		Objects.requireNonNull(targetPassword, "targetPassword");
		try (var sourcePool = PlanningJdbc.open(source, sourcePassword);
				var targetPool = PlanningJdbc.open(target, targetPassword);
				var sourceConnection = sourcePool.getConnection();
				var targetConnection = targetPool.getConnection()) {
			PlanningJdbc.requireDialect(sourceConnection);
			PlanningJdbc.requireDialect(targetConnection);
			final var resolution = new BulkMigrationJobConfigurationResolver().resolveJob(
					request.configurationFile().toFile(), sourceConnection, request.expectedConfigurationFingerprint());
			final var original = resolution.plan();
			BulkMigrationTargetValidator.validate(targetConnection, original);
			final var tasks = new ArrayList<BulkMigrationJobTask>();
			for (final var task : original.getTasks()) {
				if (task.getOptions().getCheckpointMode() != BulkMigrationCheckpointMode.DATABASE) {
					tasks.add(task);
					continue;
				}
				tasks.add(
						BulkMigrationJobTask.builder().taskId(task.getTaskId()).sourceTable(task.getSourceTable())
								.keysetSource(task.getKeysetSource()).targetTable(task.getTargetTable())
								.columnMappings(task.getColumnMappings())
								.requireEmptyTarget(task.isRequireEmptyTarget()).options(task.getOptions())
								.chunkListener(task.getChunkListener()).checkpointStore(JdbcBulkMigrationCheckpointStore
										.readOnly(targetConnection, task.getOptions().getCheckpointTableName()))
								.build());
			}
			final var plan = BulkMigrationJobPlanner.plan(original.getJobId(), tasks, original.getLifecycle());
			if (!original.getFingerprint().equals(plan.getFingerprint())) {
				throw new IllegalStateException("Read-only checkpoint binding changed the migration plan fingerprint.");
			}
			final var state = BulkMigrationJobStatusInspector.inspect(plan);
			final var report = new BulkMigrationOperationalReportBuilder().build(plan, state, null, null, Map.of(),
					null, resolution.provenance());
			final var base = new ReportReviewService().operational(report);
			final List<List<String>> rows = new ArrayList<>();
			for (int index = 0; index < plan.getTasks().size(); index++) {
				final var task = plan.getTasks().get(index);
				rows.add(List.of(String.valueOf(index + 1), task.getTaskId(), identity(task.getEffectiveSourceTable()),
						identity(task.getEffectiveTargetTable()),
						String.valueOf(task.getOptions().getIncrementalStrategy()),
						String.valueOf(task.getOptions().getChunkSize()),
						String.valueOf(task.getOptions().getCheckpointMode()), report.tasks().get(index).state().name(),
						task.getColumnMappings().isEmpty() ? "Same column names"
								: task.getColumnMappings().entrySet().stream().sorted(Map.Entry.comparingByKey())
										.map(entry -> entry.getKey() + " → " + entry.getValue())
										.collect(java.util.stream.Collectors.joining(", "))));
			}
			final byte[] configurationBytes = Files.readAllBytes(request.configurationFile());
			if (!resolution.provenance().configurationFingerprint()
					.equals("sha256:" + com.sqlapp.util.MessageDigests.SHA256.checksumAsString(configurationBytes))) {
				throw new IllegalArgumentException("Configuration changed during inspection. Inspect again.");
			}
			final var configuration = new com.sqlapp.util.YamlConverter().fromJsonString(
					new String(configurationBytes, java.nio.charset.StandardCharsets.UTF_8),
					com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfiguration.class);
			final String restriction = configuration.getSchemaFingerprint() == null
					|| configuration.getSchemaFingerprint().isBlank()
							? "GUI execution requires schemaFingerprint in job YAML. Set the Schema XML SHA-256 and inspect again."
							: resolution.provenance().assessmentReportFingerprint() != null
									|| resolution.provenance().ddlVerificationReportFingerprint() != null
											? "Jobs requiring assessment/DDL approval artifacts must use the command interface."
											: !report.compatible()
													? "Resolve incompatible checkpoints before execution."
													: null;
			final String pauseRestriction = configuration.getTasks().stream().allMatch(task -> task.isResume()
					&& task.isUseTransaction()
					&& (task.getMode() != com.sqlapp.jdbc.bulk.BulkMigrationMode.UPSERT || task
							.getDuplicateKeyStrategy() == com.sqlapp.jdbc.bulk.BulkUpsertDuplicateKeyStrategy.KEEP_LAST))
									? null
									: "Pause requires resume and transactions for every task; UPSERT also requires duplicateKeyStrategy: KEEP_LAST.";
			final var snapshot = new ExecutionSnapshot(
					com.sqlapp.data.db.command.migration.schema.MigrationPlanCommand.databaseIdentity(sourceConnection),
					com.sqlapp.data.db.command.migration.schema.MigrationPlanCommand.databaseIdentity(targetConnection),
					restriction, pauseRestriction);
			final String outputs = "\nConfigured lease: " + resolution.leaseConfiguration()
					+ "\nConfigured operational report: " + resolution.reportConfiguration()
					+ "\nConfigured verification: " + resolution.verificationConfiguration();
			final String summary = base.summary()
					+ "\nLive target table/column/key checks passed. No rows were migrated."
					+ "\nLease, maintenance, approval artifacts and data equality were not verified."
					+ "\nConfiguration fingerprint: " + resolution.provenance().configurationFingerprint() + outputs
					+ "\nPause: "
					+ (pauseRestriction == null ? "available at a committed chunk boundary" : pauseRestriction)
					+ "\nGUI execution: " + (restriction == null ? "available after confirmation" : restriction);
			return new Result(request.configurationFile(), report,
					new ReportReviewService.Review(summary,
							List.of("Order", "Task", "Source table", "Target table", "Strategy", "Chunk size",
									"Checkpoint", "Saved state", "Column mappings"),
							rows, base.details()),
					snapshot, resolution.verificationConfiguration());
		} catch (final Exception failure) {
			String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
			for (final char[] password : new char[][] { sourcePassword, targetPassword }) {
				if (password.length > 0) {
					message = message.replace(new String(password), "[redacted]");
				}
			}
			for (final var spec : new PlanningJdbc.Spec[] { source, target }) {
				if (spec != null) {
					message = message.replace(spec.jdbcUrl(), "[JDBC URL]");
				}
			}
			throw new IllegalArgumentException("Unable to build data migration plan: " + message);
		} finally {
			Arrays.fill(sourcePassword, '\0');
			Arrays.fill(targetPassword, '\0');
		}
	}

	void save(final Result result, final Path destination) throws java.io.IOException {
		Objects.requireNonNull(result, "result");
		if (destination == null || destination.getFileName() == null
				|| !destination.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")) {
			throw new IllegalArgumentException("Choose a .json file for the data migration status report.");
		}
		if (destination.toAbsolutePath().normalize().equals(result.configurationFile())
				|| Files.exists(destination) && Files.isSameFile(destination, result.configurationFile())) {
			throw new IllegalArgumentException("Choose a destination different from the migration configuration file.");
		}
		final var io = new BulkMigrationOperationalReportIO();
		io.write(destination, result.report());
		if (!result.report().equals(io.read(destination))) {
			throw new java.io.IOException("Saved report differs from the displayed data migration snapshot.");
		}
	}

	private static String identity(final Table table) {
		return java.util.stream.Stream.of(table.getCatalogName(), table.getSchemaName(), table.getName())
				.filter(value -> value != null && !value.isBlank()).collect(java.util.stream.Collectors.joining("."));
	}
}
