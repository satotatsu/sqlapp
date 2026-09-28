/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.migration.assessment;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.ObjectId;

/** Shared table/column/key DDL assembly with dialect-supplied identifier quoting. */
public final class MigrationTargetDdlGenerator {
	private MigrationTargetDdlGenerator() { }

	public static String generate(final MigrationAssessmentSource source, final ResolvedMigrationTargetMapping mapping,
			final Function<String, String> quote, final String batchSeparator) {
		return generate(source, mapping, quote, batchSeparator, column -> "");
	}

	public static String generate(final MigrationAssessmentSource source, final ResolvedMigrationTargetMapping mapping,
			final Function<String, String> quote, final String batchSeparator,
			final Function<ResolvedMigrationTargetMapping.ColumnMapping, String> identityClause) {
		final var sourceTables = new HashMap<ObjectId, Table>();
		for (final var schema : source.schemas()) {
			for (final var table : schema.getTables()) { sourceTables.put(tableId(table), table); }
		}
		final var mappedTables = new HashMap<ObjectId, ResolvedMigrationTargetMapping.TableMapping>();
		mapping.tables().forEach(table -> mappedTables.put(table.sourceTable(), table));
		final var sql = new StringBuilder("-- Review-only DDL. Unnamed primary/foreign keys are emitted only when every participating object is mapped.\n")
				.append("-- Indexes, defaults, conversion expressions and cascade rules are not included.\n");
		for (final var mapped : mapping.tables()) {
			final Table sourceTable = sourceTables.get(mapped.sourceTable());
			final Map<String, String> columns = columns(mapped);
			final String primaryKey = primaryKey(sourceTable, columns, quote);
			final var primaryKeyColumns = primaryKeyColumns(sourceTable, primaryKey == null);
			sql.append("CREATE TABLE ").append(name(mapped, quote)).append(" (\n");
			for (int i = 0; i < mapped.columns().size(); i++) {
				final var column = mapped.columns().get(i);
				sql.append("  ").append(quote.apply(column.targetColumn())).append(' ').append(column.targetType())
						.append(Boolean.TRUE.equals(column.identity()) ? identityClause.apply(column) : "");
				if (primaryKeyColumns.contains(key(column.sourceColumn().name())) || Boolean.FALSE.equals(column.nullable())) { sql.append(" NOT NULL"); }
				else if (Boolean.TRUE.equals(column.nullable())) { sql.append(" NULL"); }
				if (i + 1 < mapped.columns().size() || primaryKey != null) { sql.append(','); }
				sql.append('\n');
			}
			if (primaryKey != null) { sql.append("  PRIMARY KEY (").append(primaryKey).append(")\n"); }
			sql.append(");\n").append(batchSeparator);
		}
		for (final var mapped : mapping.tables()) {
			final Table sourceTable = sourceTables.get(mapped.sourceTable());
			if (sourceTable == null) { continue; }
			final Map<String, String> childColumns = columns(mapped);
			for (final var foreignKey : sourceTable.getConstraints().getForeignKeyConstraints()) {
				final Table related = foreignKey.getRelatedTable();
				final var parent = related == null ? null : mappedTables.get(tableId(related));
				if (parent == null || foreignKey.getColumns().size() != foreignKey.getRelatedColumns().size()) { continue; }
				final Map<String, String> parentColumns = columns(parent);
				final var child = new StringBuilder();
				final var referenced = new StringBuilder();
				boolean complete = true;
				for (int i = 0; i < foreignKey.getColumns().size(); i++) {
					final String childName = childColumns.get(key(foreignKey.getColumns().get(i).getName()));
					final String parentName = parentColumns.get(key(foreignKey.getRelatedColumns().get(i).getName()));
					if (childName == null || parentName == null) { complete = false; break; }
					if (i > 0) { child.append(", "); referenced.append(", "); }
					child.append(quote.apply(childName));
					referenced.append(quote.apply(parentName));
				}
				if (complete) {
					sql.append("ALTER TABLE ").append(name(mapped, quote)).append(" ADD FOREIGN KEY (")
							.append(child).append(") REFERENCES ").append(name(parent, quote)).append(" (")
							.append(referenced).append(");\n").append(batchSeparator);
				}
			}
		}
		return sql.toString();
	}

	private static String primaryKey(final Table table, final Map<String, String> columns,
			final Function<String, String> quote) {
		if (table == null) { return null; }
		final var primaryKey = table.getConstraints().getPrimaryKeyConstraint();
		if (primaryKey == null || primaryKey.getColumns().isEmpty()) { return null; }
		final var value = new StringBuilder();
		for (int i = 0; i < primaryKey.getColumns().size(); i++) {
			final String target = columns.get(key(primaryKey.getColumns().get(i).getName()));
			if (target == null) { return null; }
			if (i > 0) { value.append(", "); }
			value.append(quote.apply(target));
		}
		return value.toString();
	}
	private static java.util.Set<String> primaryKeyColumns(final Table table, final boolean omitted) {
		if (table == null || omitted || table.getConstraints().getPrimaryKeyConstraint() == null) { return java.util.Set.of(); }
		final var result = new java.util.HashSet<String>();
		table.getConstraints().getPrimaryKeyConstraint().getColumns().forEach(column -> result.add(key(column.getName())));
		return result;
	}

	private static Map<String, String> columns(final ResolvedMigrationTargetMapping.TableMapping table) {
		final var result = new HashMap<String, String>();
		table.columns().forEach(column -> result.put(key(column.sourceColumn().name()), column.targetColumn()));
		return result;
	}
	private static String name(final ResolvedMigrationTargetMapping.TableMapping table, final Function<String, String> quote) {
		return (table.targetSchema() == null || table.targetSchema().isBlank() ? "" : quote.apply(table.targetSchema()) + ".")
				+ quote.apply(table.targetTable());
	}
	private static ObjectId tableId(final Table table) {
		return new ObjectId(table.getCatalogName(), table.getSchemaName(), "table", table.getName());
	}
	private static String key(final String value) { return value.toUpperCase(java.util.Locale.ROOT); }
}
