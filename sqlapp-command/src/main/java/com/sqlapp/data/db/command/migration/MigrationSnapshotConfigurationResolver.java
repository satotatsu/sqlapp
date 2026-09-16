/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaCollection;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.TableCollection;
import com.sqlapp.data.schemas.migration.MigrationSnapshotDefinition;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.YamlConverter;

/** Resolves snapshot YAML names against the canonical Schema model. */
public final class MigrationSnapshotConfigurationResolver {
	public record Resolution(Table sourceTable, Table targetTable, MigrationSnapshotDefinition definition,
			java.time.Instant effectiveAt, int fetchSize, int batchSize) { }

	public Resolution resolve(final File configurationFile) {
		if (configurationFile == null || !configurationFile.isFile()) {
			throw new CommandException("Migration snapshot configuration file is required.");
		}
		final var value = new YamlConverter().fromJsonString(configurationFile, MigrationSnapshotConfiguration.class);
		if (value == null || value.getSchemaFile() == null || value.getSchemaFile().isBlank()) {
			throw new CommandException("schemaFile is required in migration snapshot configuration.");
		}
		if (value.getEffectiveAt() == null) {
			throw new CommandException("effectiveAt is required in migration snapshot configuration.");
		}
		if (value.getFetchSize() <= 0 || value.getBatchSize() <= 0) {
			throw new CommandException("fetchSize and batchSize must be greater than zero.");
		}
		final File schemaFile = resolve(configurationFile, value.getSchemaFile());
		final List<Table> tables = readTables(schemaFile);
		final Table source = findTable(tables, value.getSourceTable(), "sourceTable");
		final Table target = findTable(tables, value.getTargetTable(), "targetTable");
		final MigrationSnapshotDefinition definition;
		try {
			definition = new MigrationSnapshotDefinition(value.getSourceTable(), target.getName(), value.getKeyColumns(),
					value.getTrackedColumns(), value.getValidFromColumn(), value.getValidToColumn(),
					value.getCurrentColumn(), value.isExpireMissingRows());
		} catch (IllegalArgumentException e) {
			throw new CommandException("Invalid migration snapshot definition: " + e.getMessage(), e);
		}
		validateColumns(source, definition.keyColumns(), "sourceTable", value.getSourceTable());
		validateColumns(source, definition.trackedColumns(), "sourceTable", value.getSourceTable());
		validateColumns(target, definition.keyColumns(), "targetTable", value.getTargetTable());
		validateColumns(target, definition.trackedColumns(), "targetTable", value.getTargetTable());
		validateColumns(target, List.of(definition.validFromColumn(), definition.validToColumn()), "targetTable",
				value.getTargetTable());
		if (definition.currentColumn() != null) {
			validateColumns(target, List.of(definition.currentColumn()), "targetTable", value.getTargetTable());
		}
		return new Resolution(source, target, definition, value.getEffectiveAt(), value.getFetchSize(),
				value.getBatchSize());
	}

	private static void validateColumns(final Table table, final List<String> names, final String property,
			final String configuredName) {
		for (final String name : names) {
			if (table.getColumns().get(name) == null) {
				throw new CommandException("Unknown column '" + name + "' in " + property + ": " + configuredName);
			}
		}
	}

	private static File resolve(final File configurationFile, final String path) {
		final File file = new File(path);
		return file.isAbsolute() ? file : new File(configurationFile.getAbsoluteFile().getParentFile(), path);
	}

	private static List<Table> readTables(final File file) {
		if (!file.isFile()) throw new CommandException("Schema XML does not exist: " + file);
		try { return tables(SchemaUtils.readXml(file)); }
		catch (IOException e) { throw new CommandException("Failed to read Schema XML: " + file, e); }
	}

	private static List<Table> tables(final DbCommonObject<?> root) {
		if (root instanceof Table table) return List.of(table);
		if (root instanceof TableCollection collection) return List.copyOf(collection);
		if (root instanceof Schema schema) return List.copyOf(schema.getTables());
		if (root instanceof SchemaCollection schemas) return schemas.stream().flatMap(x -> x.getTables().stream()).toList();
		if (root instanceof Catalog catalog) return catalog.getSchemas().stream().flatMap(x -> x.getTables().stream()).toList();
		throw new CommandException("Schema XML must contain tables.");
	}

	private static Table findTable(final List<Table> tables, final String name, final String property) {
		if (name == null || name.isBlank()) throw new CommandException(property + " is required.");
		final String[] parts = name.split("\\.", -1);
		if (parts.length > 3) throw new CommandException("Invalid " + property + ": " + name);
		final List<Table> matches = tables.stream().filter(t -> matches(t, parts)).toList();
		if (matches.isEmpty()) throw new CommandException(property + " was not found in Schema XML: " + name);
		if (matches.size() > 1) throw new CommandException("Ambiguous " + property + " in Schema XML: " + name);
		return matches.getFirst();
	}

	private static boolean matches(final Table table, final String[] parts) {
		if (!equalsName(table.getName(), parts[parts.length - 1])) return false;
		if (parts.length >= 2 && !equalsName(table.getSchemaName(), parts[parts.length - 2])) return false;
		return parts.length < 3 || equalsName(table.getCatalogName(), parts[0]);
	}

	private static boolean equalsName(final String actual, final String expected) {
		return actual != null && actual.equalsIgnoreCase(expected);
	}
}
