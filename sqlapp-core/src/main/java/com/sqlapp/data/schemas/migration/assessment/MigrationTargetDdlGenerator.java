/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.migration.assessment;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import com.sqlapp.data.schemas.Order;
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
		return generate(source, mapping, quote, batchSeparator, identityClause, name -> false);
	}

	public static String generate(final MigrationAssessmentSource source, final ResolvedMigrationTargetMapping mapping,
			final Function<String, String> quote, final String batchSeparator,
			final Function<ResolvedMigrationTargetMapping.ColumnMapping, String> identityClause,
			final Predicate<String> usableSourceName) {
		final var sourceTables = new HashMap<ObjectId, Table>();
		for (final var schema : source.schemas()) {
			for (final var table : schema.getTables()) { sourceTables.put(tableId(table), table); }
		}
		final var mappedTables = new HashMap<ObjectId, ResolvedMigrationTargetMapping.TableMapping>();
		mapping.tables().forEach(table -> mappedTables.put(table.sourceTable(), table));
		final var names = new NameRegistry(usableSourceName);
		final var sql = new StringBuilder("-- Review-only DDL. Keys and secondary indexes are emitted only when every participating object is mapped.\n")
				.append("-- Nullable unique keys, source defaults, conversion expressions and cascade rules are not included.\n");
		for (final var mapped : mapping.tables()) {
			final Table sourceTable = sourceTables.get(mapped.sourceTable());
			final Map<String, String> columns = columns(mapped);
			final String primaryKey = primaryKey(sourceTable, columns, quote);
			final var uniqueKeys = uniqueKeys(sourceTable, mapped, columns, quote);
			final var checks = mapped.checkExpressions();
			final var primaryKeyColumns = primaryKeyColumns(sourceTable, primaryKey == null);
			sql.append("CREATE TABLE ").append(name(mapped, quote)).append(" (\n");
			for (int i = 0; i < mapped.columns().size(); i++) {
				final var column = mapped.columns().get(i);
				sql.append("  ").append(quote.apply(column.targetColumn())).append(' ').append(column.targetType())
						.append(Boolean.TRUE.equals(column.identity()) ? identityClause.apply(column) : "");
				if (column.defaultExpression() != null && !column.defaultExpression().isBlank()) {
					sql.append(" DEFAULT ").append(column.defaultExpression());
				}
				if (primaryKeyColumns.contains(key(column.sourceColumn().name())) || Boolean.FALSE.equals(column.nullable())) { sql.append(" NOT NULL"); }
				else if (Boolean.TRUE.equals(column.nullable())) { sql.append(" NULL"); }
				if (i + 1 < mapped.columns().size() || primaryKey != null || !uniqueKeys.isEmpty() || !checks.isEmpty()) { sql.append(','); }
				sql.append('\n');
			}
			final var constraints = new java.util.ArrayList<String>();
			if (primaryKey != null) {
				final String sourceName = sourceTable.getConstraints().getPrimaryKeyConstraint().getName();
				constraints.add("CONSTRAINT " + quote.apply(names.choose(mapped.targetSchema(), "PK", sourceName,
						objectIdentity(mapped) + ".primary"))
						+ " PRIMARY KEY (" + primaryKey + ")");
			}
			for (int i = 0; i < uniqueKeys.size(); i++) {
				final var unique = uniqueKeys.get(i);
				constraints.add("CONSTRAINT " + quote.apply(names.choose(mapped.targetSchema(), "UK", unique.sourceName(),
						objectIdentity(mapped) + ".unique." + i)) + " UNIQUE (" + unique.columns() + ")");
			}
			checks.forEach(expression -> constraints.add("CHECK (" + expression + ")"));
			for (int i = 0; i < constraints.size(); i++) {
				sql.append("  ").append(constraints.get(i));
				if (i + 1 < constraints.size()) { sql.append(','); }
				sql.append('\n');
			}
				sql.append(");\n").append(batchSeparator);
		}
		for (final var mapped : mapping.tables()) {
			final Table sourceTable = sourceTables.get(mapped.sourceTable());
			if (sourceTable == null) { continue; }
			final Map<String, String> targetColumns = columns(mapped);
			int ordinal = 0;
			for (final var index : sourceTable.getIndexes()) {
				if (index.isUnique() || index.getColumns().isEmpty()) { continue; }
				ordinal++;
				final var keyColumns = new StringBuilder();
				boolean complete = true;
				for (int i = 0; i < index.getColumns().size(); i++) {
					final var sourceColumn = index.getColumns().get(i);
					final String targetColumn = targetColumns.get(key(sourceColumn.getName()));
					if (targetColumn == null) { complete = false; break; }
					if (i > 0) { keyColumns.append(", "); }
					keyColumns.append(quote.apply(targetColumn));
					if (sourceColumn.getOrder() == Order.Desc) { keyColumns.append(" DESC"); }
				}
				if (complete) {
					final String identity = objectIdentity(mapped) + "."
							+ index.getName() + "." + ordinal;
					sql.append("CREATE INDEX ").append(quote.apply(names.choose(mapped.targetSchema(), "IX", index.getName(), identity))).append(" ON ")
							.append(name(mapped, quote)).append(" (").append(keyColumns).append(");\n")
							.append(batchSeparator);
				}
			}
		}
		for (final var mapped : mapping.tables()) {
			final Table sourceTable = sourceTables.get(mapped.sourceTable());
			if (sourceTable == null) { continue; }
			final Map<String, String> childColumns = columns(mapped);
			int ordinal = 0;
			for (final var foreignKey : sourceTable.getConstraints().getForeignKeyConstraints()) {
				ordinal++;
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
					final String identity = objectIdentity(mapped) + ".foreign." + ordinal + "." + objectIdentity(parent);
					sql.append("ALTER TABLE ").append(name(mapped, quote)).append(" ADD CONSTRAINT ")
							.append(quote.apply(names.choose(mapped.targetSchema(), "FK", foreignKey.getName(), identity))).append(" FOREIGN KEY (")
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
	private static java.util.List<Key> uniqueKeys(final Table table,
			final ResolvedMigrationTargetMapping.TableMapping mapped, final Map<String, String> columns,
			final Function<String, String> quote) {
		if (table == null) { return java.util.List.of(); }
		final var nullable = new HashMap<String, Boolean>();
		mapped.columns().forEach(column -> nullable.put(key(column.sourceColumn().name()), column.nullable()));
		final var result = new java.util.ArrayList<Key>();
		for (final var unique : table.getConstraints().getUniqueConstraints()) {
			if (unique.isPrimaryKey() || unique.getColumns().isEmpty()) { continue; }
			final var value = new StringBuilder();
			boolean completeAndRequired = true;
			for (int i = 0; i < unique.getColumns().size(); i++) {
				final String sourceName = unique.getColumns().get(i).getName();
				final String targetName = columns.get(key(sourceName));
				final var sourceColumn = table.getColumns().get(sourceName);
				if (targetName == null || sourceColumn == null || !sourceColumn.isNotNull()
						|| !Boolean.FALSE.equals(nullable.get(key(sourceName)))) {
					completeAndRequired = false;
					break;
				}
				if (i > 0) { value.append(", "); }
				value.append(quote.apply(targetName));
			}
			if (completeAndRequired) { result.add(new Key(unique.getName(), value.toString())); }
		}
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
	private static String objectIdentity(final ResolvedMigrationTargetMapping.TableMapping table) {
		return (table.targetSchema() == null ? "" : table.targetSchema()) + "." + table.targetTable();
	}
	private static String generatedName(final String prefix, final String identity) {
		try {
			final byte[] digest = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
			final var value = new StringBuilder(prefix).append('_');
			for (int i = 0; i < 6; i++) { value.append(String.format("%02x", digest[i])); }
			return value.toString();
		}
		catch (final java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
	}
	private record Key(String sourceName, String columns) { }
	private static final class NameRegistry {
		private final Predicate<String> usableSourceName;
		private final java.util.Set<String> used = new java.util.HashSet<>();
		private NameRegistry(final Predicate<String> usableSourceName) { this.usableSourceName = usableSourceName; }
		private String choose(final String schema, final String prefix, final String sourceName, final String identity) {
			if (sourceName != null && !sourceName.isBlank() && usableSourceName.test(sourceName)
					&& used.add((schema == null ? "" : key(schema)) + "." + key(sourceName))) { return sourceName; }
			int salt = 0;
			while (true) {
				final String generated = generatedName(prefix, identity + (salt == 0 ? "" : "." + salt));
				if (used.add((schema == null ? "" : key(schema)) + "." + key(generated))) { return generated; }
				salt++;
			}
		}
	}
}
