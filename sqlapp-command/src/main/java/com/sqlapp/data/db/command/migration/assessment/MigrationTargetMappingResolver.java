/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.assessment;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.ObjectId;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessmentSource;
import com.sqlapp.data.schemas.migration.assessment.MigrationTargetMapping;
import com.sqlapp.data.schemas.migration.assessment.ResolvedMigrationTargetMapping;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.YamlConverter;

/** Reads and resolves concise mapping names against the canonical source Schema. */
final class MigrationTargetMappingResolver {
	ResolvedMigrationTargetMapping resolve(final File file, final String fingerprint, final String sourceFingerprint,
			final String targetDatabase, final String targetVersion, final MigrationAssessmentSource source) {
		final MigrationTargetMapping mapping;
		try {
			final var converter = new YamlConverter();
			// JsonConverter's current feature switch follows its configured strict/indent mode.
			converter.setIndentOutput(true);
			converter.setFailOnUnknownProperties(true);
			mapping = converter.fromJsonString(file, MigrationTargetMapping.class);
		}
		catch (final RuntimeException e) { throw new CommandException("Failed to read migration mapping: " + file, e); }
		if (mapping == null || !MigrationTargetMapping.FORMAT.equals(mapping.format())
				|| mapping.version() != MigrationTargetMapping.CURRENT_VERSION) {
			throw new CommandException("Mapping format must be " + MigrationTargetMapping.FORMAT + " version 1");
		}
		if (!sourceFingerprint.equals(mapping.sourceFingerprint())) {
			throw new CommandException("Mapping sourceFingerprint does not match inputFile");
		}
		if (!targetDatabase.equalsIgnoreCase(text(mapping.targetDatabase())) || !targetVersion.equalsIgnoreCase(text(mapping.targetVersion()))) {
			throw new CommandException("Mapping targetDatabase/targetVersion does not match the requested target");
		}
		if (mapping.tables().isEmpty()) { throw new CommandException("Mapping must contain at least one table"); }
		final var tables = new ArrayList<ResolvedMigrationTargetMapping.TableMapping>();
		final Set<ObjectId> sourceTables = new HashSet<>();
		final Set<String> targets = new HashSet<>();
		for (final var configured : mapping.tables()) {
			final Table table = table(source.schemas(), configured);
			final Schema schema = table.getSchema();
			final var tableId = new ObjectId(schema.getCatalogName(), schema.getName(), "table", table.getName());
			if (!sourceTables.add(tableId)) { throw new CommandException("Duplicate source table mapping: " + table.getName()); }
			final String targetTable = optional(configured.targetTable(), table.getName());
			final String targetKey = canonical(configured.targetSchema()) + "." + canonical(targetTable);
			if (!targets.add(targetKey)) { throw new CommandException("Duplicate target table mapping: " + targetKey); }
			final var columns = new ArrayList<ResolvedMigrationTargetMapping.ColumnMapping>();
			final Set<String> sourceColumns = new HashSet<>();
			final Set<String> targetColumns = new HashSet<>();
			for (final var configuredColumn : configured.columns()) {
				final Column column = column(table, configuredColumn.sourceColumn());
				if (!sourceColumns.add(canonical(column.getName()))) { throw new CommandException("Duplicate source column mapping: " + table.getName() + "." + column.getName()); }
				final String targetColumn = optional(configuredColumn.targetColumn(), column.getName());
				if (!targetColumns.add(canonical(targetColumn))) { throw new CommandException("Duplicate target column mapping: " + targetTable + "." + targetColumn); }
				if (configuredColumn.targetType() == null || configuredColumn.targetType().isBlank()) {
					throw new CommandException("targetType is required for " + table.getName() + "." + column.getName());
				}
				final String defaultExpression = defaultExpression(configuredColumn.defaultExpression(), table, column,
						configuredColumn.identity());
				columns.add(new ResolvedMigrationTargetMapping.ColumnMapping(
						new ObjectId(schema.getCatalogName(), schema.getName(), "column", column.getName(), table.getName()),
						targetColumn, configuredColumn.targetType().trim(), configuredColumn.nullable(),
						configuredColumn.identity(), defaultExpression, configuredColumn.conversion()));
			}
			if (columns.isEmpty()) { throw new CommandException("Mapping table must contain at least one column: " + table.getName()); }
			tables.add(new ResolvedMigrationTargetMapping.TableMapping(tableId,
					blank(configured.targetSchema()) ? null : configured.targetSchema().trim(), targetTable, columns));
		}
		return new ResolvedMigrationTargetMapping(fingerprint, targetDatabase.toLowerCase(Locale.ROOT), targetVersion, tables);
	}

	private static Table table(final List<Schema> schemas, final MigrationTargetMapping.TableMapping value) {
		if (value.sourceTable() == null || value.sourceTable().isBlank()) { throw new CommandException("sourceTable is required"); }
		final var matches = schemas.stream().filter(s -> blank(value.sourceCatalog()) || equal(s.getCatalogName(), value.sourceCatalog()))
				.filter(s -> blank(value.sourceSchema()) || equal(s.getName(), value.sourceSchema()))
				.flatMap(s -> s.getTables().stream()).filter(t -> equal(t.getName(), value.sourceTable())).toList();
		if (matches.size() != 1) { throw new CommandException("sourceTable must resolve uniquely: " + value.sourceTable() + " (found " + matches.size() + ")"); }
		return matches.getFirst();
	}
	private static Column column(final Table table, final String name) {
		if (name == null || name.isBlank()) { throw new CommandException("sourceColumn is required for table " + table.getName()); }
		final var matches = table.getColumns().stream().filter(c -> equal(c.getName(), name)).toList();
		if (matches.size() != 1) { throw new CommandException("sourceColumn must resolve uniquely: " + table.getName() + "." + name); }
		return matches.getFirst();
	}
	private static boolean equal(final String left, final String right) { return left != null && right != null && left.equalsIgnoreCase(right.trim()); }
	private static boolean blank(final String value) { return value == null || value.isBlank(); }
	private static String optional(final String value, final String fallback) { return blank(value) ? fallback : value.trim(); }
	private static String canonical(final String value) { return blank(value) ? "" : value.trim().toUpperCase(Locale.ROOT); }
	private static String text(final String value) { return value == null ? "" : value.trim(); }
	private static String defaultExpression(final String value, final Table table, final Column column,
			final Boolean identity) {
		if (blank(value)) { return null; }
		final String expression = value.trim();
		if (Boolean.TRUE.equals(identity)) {
			throw new CommandException("defaultExpression cannot be combined with identity: " + table.getName() + "." + column.getName());
		}
		if (expression.indexOf(';') >= 0 || expression.indexOf('\r') >= 0 || expression.indexOf('\n') >= 0
				|| expression.contains("--") || expression.contains("/*") || expression.contains("*/")) {
			throw new CommandException("defaultExpression must be one comment-free SQL expression: "
					+ table.getName() + "." + column.getName());
		}
		return expression;
	}
}
