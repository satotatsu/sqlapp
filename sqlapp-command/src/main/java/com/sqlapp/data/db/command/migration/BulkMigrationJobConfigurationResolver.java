/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaCollection;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.TableCollection;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlan;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlanner;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTask;
import com.sqlapp.jdbc.bulk.BulkMigrationRetryOption;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationOption;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationKeysetSource;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationJobLeaseStore;
import com.sqlapp.util.YamlConverter;

/** Resolves a portable YAML job definition against a captured Schema model. */
public class BulkMigrationJobConfigurationResolver {
	public record Resolution(BulkMigrationJobPlan plan,
			BulkMigrationJobLeaseConfiguration leaseConfiguration,
			OperationalReportConfiguration reportConfiguration,
			VerificationConfiguration verificationConfiguration) {
	}

	public record OperationalReportConfiguration(java.nio.file.Path targetFile,
			BulkMigrationOperationalReportFailurePolicy failurePolicy) {
	}

	public record VerificationConfiguration(int chunkSize, boolean failOnMismatch,
			java.nio.file.Path targetFile, Map<String, List<String>> columnsByTask,
			BulkMigrationVerificationIsolation isolation) {
	}

	public BulkMigrationJobPlan resolve(final File configurationFile,
			final Connection sourceConnection) {
		return resolveJob(configurationFile, sourceConnection).plan();
	}

	public Resolution resolveJob(final File configurationFile,
			final Connection sourceConnection) {
		if (configurationFile == null || !configurationFile.isFile()) {
			throw new CommandException("Bulk migration configuration file is required.");
		}
		final BulkMigrationJobConfiguration configuration = new YamlConverter()
				.fromJsonString(configurationFile, BulkMigrationJobConfiguration.class);
		if (configuration == null || configuration.getSchemaFile() == null
				|| configuration.getSchemaFile().isBlank()) {
			throw new CommandException("schemaFile is required in bulk migration configuration.");
		}
		final File schemaFile = resolve(configurationFile, configuration.getSchemaFile());
		final List<Table> tables = readTables(schemaFile);
		final List<BulkMigrationJobTask> tasks = new ArrayList<>();
		if (configuration.getTasks() == null) {
			throw new CommandException("tasks must not be null in bulk migration configuration.");
		}
		for (final var task : configuration.getTasks()) {
			final Table table = findTable(tables, task.getTable());
			validateVerificationColumns(configuration.getVerification(), task, table);
			final BulkOption bulk = bulk(task.getBulk());
			final BulkMigrationRetryOption retry = retry(task.getRetry());
			final var upsert = BulkUpsertOption.builder()
					.keyColumns(task.getKeyColumns()).updateColumns(task.getUpdateColumns())
					.updateWhenMatched(task.isUpdateWhenMatched())
					.insertWhenNotMatched(task.isInsertWhenNotMatched())
					.useTransaction(task.isUseTransaction())
					.duplicateKeyStrategy(task.getDuplicateKeyStrategy())
					.stagingTableName(task.getStagingTableName()).bulkOption(bulk).build();
			final var options = ChunkedBulkMigrationOption.builder()
					.migrationId(value(task.getMigrationId(), task.getId()))
					.chunkSize(task.getChunkSize()).mode(task.getMode()).resume(task.isResume())
					.checkpointMode(task.getCheckpointMode())
					.checkpointTableName(task.getCheckpointTableName())
					.sourceFingerprint(task.getSourceFingerprint())
					.targetFingerprint(task.getTargetFingerprint()).bulkOption(bulk)
					.bulkUpsertOption(upsert).retryOption(retry).build();
			final JdbcBulkMigrationKeysetSource source = task.getKeysetColumns() == null
					|| task.getKeysetColumns().isEmpty()
							? new JdbcBulkMigrationKeysetSource(sourceConnection, table)
							: new JdbcBulkMigrationKeysetSource(sourceConnection, table,
									task.getKeysetColumns());
			final var builder = BulkMigrationJobTask.builder().taskId(task.getId())
					.keysetSource(source).options(options);
			if (task.getCheckpointMode()
					== com.sqlapp.jdbc.bulk.BulkMigrationCheckpointMode.FILE) {
				if (task.getCheckpointDirectory() == null
						|| task.getCheckpointDirectory().isBlank()) {
					throw new CommandException("checkpointDirectory is required for FILE "
							+ "checkpoint mode: " + task.getId());
				}
				builder.checkpointStore(new FileBulkMigrationCheckpointStore(
						resolve(configurationFile, task.getCheckpointDirectory()).toPath()));
			} else if (task.getCheckpointMode()
					== com.sqlapp.jdbc.bulk.BulkMigrationCheckpointMode.CUSTOM) {
				throw new CommandException("CUSTOM checkpoint mode is only available for "
						+ "programmatic plans: " + task.getId());
			} else if (task.getCheckpointDirectory() != null
					&& !task.getCheckpointDirectory().isBlank()) {
				throw new CommandException("checkpointDirectory requires FILE checkpoint mode: "
						+ task.getId());
			}
			tasks.add(builder.build());
		}
		return new Resolution(BulkMigrationJobPlanner.plan(tasks),
				lease(configurationFile, configuration.getLease()),
				report(configurationFile, configuration.getReport()),
				verification(configurationFile, configuration.getVerification(),
						configuration.getTasks()));
	}

	private static void validateVerificationColumns(
			final BulkMigrationJobConfiguration.Verification verification,
			final BulkMigrationJobConfiguration.Task task, final Table table) {
		if (verification == null || !verification.isEnabled()
				|| task.getVerificationColumns() == null
				|| task.getVerificationColumns().isEmpty()) {
			return;
		}
		final var resolvedNames = new HashSet<String>();
		for (final String name : task.getVerificationColumns()) {
			if (name == null || name.isBlank()) {
				throw new CommandException("verificationColumns must not contain an empty "
						+ "column name: " + task.getId());
			}
			final var column = table.getColumns().get(name);
			if (column == null) {
				throw new CommandException("Unknown verification column '" + name
						+ "' for task: " + task.getId());
			}
			if (!resolvedNames.add(column.getName())) {
				throw new CommandException("Duplicate verification column '" + name
						+ "' for task: " + task.getId());
			}
		}
	}

	private static VerificationConfiguration verification(final File configurationFile,
			final BulkMigrationJobConfiguration.Verification value,
			final List<BulkMigrationJobConfiguration.Task> tasks) {
		if (value == null || !value.isEnabled()) {
			return null;
		}
		if (value.getChunkSize() <= 0) {
			throw new CommandException("verification.chunkSize must be greater than zero.");
		}
		if (value.getIsolation() == null) {
			throw new CommandException("verification.isolation must not be null.");
		}
		final java.nio.file.Path targetFile = value.getTargetFile() == null
				|| value.getTargetFile().isBlank() ? null
						: resolve(configurationFile, value.getTargetFile()).toPath()
								.toAbsolutePath().normalize();
		final Map<String, List<String>> columns = new LinkedHashMap<>();
		for (final var task : tasks) {
			if (task.getVerificationColumns() != null
					&& !task.getVerificationColumns().isEmpty()) {
				columns.put(task.getId(), List.copyOf(task.getVerificationColumns()));
			}
		}
		return new VerificationConfiguration(value.getChunkSize(), value.isFailOnMismatch(),
				targetFile, Map.copyOf(columns), value.getIsolation());
	}

	private static OperationalReportConfiguration report(final File configurationFile,
			final BulkMigrationJobConfiguration.Report value) {
		if (value == null) {
			return null;
		}
		if (value.getTargetFile() == null || value.getTargetFile().isBlank()) {
			throw new CommandException("report.targetFile is required in bulk migration configuration.");
		}
		if (value.getFailurePolicy() == null) {
			throw new CommandException("report.failurePolicy must not be null.");
		}
		return new OperationalReportConfiguration(
				resolve(configurationFile, value.getTargetFile()).toPath().toAbsolutePath().normalize(),
				value.getFailurePolicy());
	}

	private static BulkMigrationJobLeaseConfiguration lease(final File configurationFile,
			final BulkMigrationJobConfiguration.Lease value) {
		if (value == null) {
			return null;
		}
		if (value.getMode() == null) {
			throw new CommandException("lease.mode is required in bulk migration configuration.");
		}
		final Duration duration;
		try {
			duration = Duration.ofSeconds(value.getDurationSeconds());
		} catch (ArithmeticException e) {
			throw new CommandException("lease.durationSeconds is out of range.", e);
		}
		if (value.getMode() == com.sqlapp.jdbc.bulk.BulkMigrationJobLeaseMode.DATABASE) {
			final String tableName = value.getTableName() == null
					|| value.getTableName().isBlank()
							? JdbcBulkMigrationJobLeaseStore.DEFAULT_TABLE_NAME
							: value.getTableName();
			return new BulkMigrationJobLeaseConfiguration(value.getMode(), value.getOwnerId(),
					duration, tableName, null);
		}
		if (value.getDirectory() == null || value.getDirectory().isBlank()) {
			throw new CommandException("lease.directory is required for FILE lease mode.");
		}
		return new BulkMigrationJobLeaseConfiguration(value.getMode(), value.getOwnerId(),
				duration, null, resolve(configurationFile, value.getDirectory()).toPath());
	}

	private static BulkOption bulk(final BulkMigrationJobConfiguration.Bulk value) {
		if (value == null) {
			return BulkOption.defaults();
		}
		return BulkOption.builder().batchSize(value.getBatchSize())
				.bulkCopyTimeout(value.getBulkCopyTimeout())
				.checkConstraints(value.isCheckConstraints()).fireTriggers(value.isFireTriggers())
				.keepIdentity(value.isKeepIdentity()).keepNulls(value.isKeepNulls())
				.tableLock(value.isTableLock()).useTransaction(value.isUseTransaction())
				.allowEncryptedValueModifications(value.isAllowEncryptedValueModifications())
				.build();
	}

	private static BulkMigrationRetryOption retry(
			final BulkMigrationJobConfiguration.Retry value) {
		if (value == null) {
			return BulkMigrationRetryOption.none();
		}
		return BulkMigrationRetryOption.builder().maxRetries(value.getMaxRetries())
				.initialBackoffMillis(value.getInitialBackoffMillis())
				.backoffMultiplier(value.getBackoffMultiplier())
				.maxBackoffMillis(value.getMaxBackoffMillis())
				.retryTransientExceptions(value.isRetryTransientExceptions())
				.sqlStates(value.getSqlStates() == null ? List.of() : value.getSqlStates())
				.errorCodes(value.getErrorCodes() == null ? List.of() : value.getErrorCodes())
				.build();
	}

	private static String value(final String value, final String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	private static File resolve(final File configurationFile, final String path) {
		final File file = new File(path);
		return file.isAbsolute() ? file
				: new File(configurationFile.getAbsoluteFile().getParentFile(), path);
	}

	private static List<Table> readTables(final File file) {
		if (!file.isFile()) {
			throw new CommandException("Schema XML does not exist: " + file);
		}
		try {
			return tables(SchemaUtils.readXml(file));
		} catch (IOException e) {
			throw new CommandException("Failed to read Schema XML: " + file, e);
		}
	}

	private static List<Table> tables(final DbCommonObject<?> root) {
		if (root instanceof Table table) {
			return List.of(table);
		}
		if (root instanceof TableCollection collection) {
			return List.copyOf(collection);
		}
		if (root instanceof Schema schema) {
			return List.copyOf(schema.getTables());
		}
		if (root instanceof SchemaCollection schemas) {
			return schemas.stream().flatMap(schema -> schema.getTables().stream()).toList();
		}
		if (root instanceof Catalog catalog) {
			return catalog.getSchemas().stream().flatMap(schema -> schema.getTables().stream()).toList();
		}
		throw new CommandException("Schema XML must contain Catalog, SchemaCollection, Schema, "
				+ "TableCollection, or Table.");
	}

	private static Table findTable(final List<Table> tables, final String name) {
		if (name == null || name.isBlank()) {
			throw new CommandException("Each bulk migration task requires a table.");
		}
		final String[] parts = name.split("\\.", -1);
		if (parts.length > 3) {
			throw new CommandException("Invalid table name: " + name);
		}
		final List<Table> matches = tables.stream().filter(table -> matches(table, parts)).toList();
		if (matches.isEmpty()) {
			throw new CommandException("Table was not found in Schema XML: " + name);
		}
		if (matches.size() > 1) {
			throw new CommandException("Ambiguous table name in Schema XML: " + name);
		}
		return matches.get(0);
	}

	private static boolean matches(final Table table, final String[] parts) {
		if (!equalsName(table.getName(), parts[parts.length - 1])) {
			return false;
		}
		if (parts.length >= 2 && !equalsName(table.getSchemaName(), parts[parts.length - 2])) {
			return false;
		}
		return parts.length < 3 || equalsName(table.getCatalogName(), parts[0]);
	}

	private static boolean equalsName(final String actual, final String expected) {
		return actual != null && actual.equalsIgnoreCase(expected);
	}
}
