/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

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
import com.sqlapp.jdbc.bulk.BulkUpsertOption;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationOption;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationKeysetSource;
import com.sqlapp.util.YamlConverter;

/** Resolves a portable YAML job definition against a captured Schema model. */
public class BulkMigrationJobConfigurationResolver {
	public BulkMigrationJobPlan resolve(final File configurationFile,
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
			final var upsert = BulkUpsertOption.builder()
					.keyColumns(task.getKeyColumns()).updateColumns(task.getUpdateColumns())
					.updateWhenMatched(task.isUpdateWhenMatched())
					.insertWhenNotMatched(task.isInsertWhenNotMatched())
					.useTransaction(task.isUseTransaction())
					.duplicateKeyStrategy(task.getDuplicateKeyStrategy())
					.stagingTableName(task.getStagingTableName()).build();
			final var options = ChunkedBulkMigrationOption.builder()
					.migrationId(value(task.getMigrationId(), task.getId()))
					.chunkSize(task.getChunkSize()).mode(task.getMode()).resume(task.isResume())
					.checkpointMode(task.getCheckpointMode())
					.checkpointTableName(task.getCheckpointTableName())
					.sourceFingerprint(task.getSourceFingerprint())
					.targetFingerprint(task.getTargetFingerprint())
					.bulkUpsertOption(upsert).build();
			final JdbcBulkMigrationKeysetSource source = task.getKeysetColumns() == null
					|| task.getKeysetColumns().isEmpty()
							? new JdbcBulkMigrationKeysetSource(sourceConnection, table)
							: new JdbcBulkMigrationKeysetSource(sourceConnection, table,
									task.getKeysetColumns());
			tasks.add(BulkMigrationJobTask.builder().taskId(task.getId())
					.keysetSource(source).options(options).build());
		}
		return BulkMigrationJobPlanner.plan(tasks);
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
