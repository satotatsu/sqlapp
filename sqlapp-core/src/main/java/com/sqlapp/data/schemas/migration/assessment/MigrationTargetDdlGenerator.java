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
		return generate(source, mapping, quote, batchSeparator, identityClause, usableSourceName, table -> "");
	}

	public static String generate(final MigrationAssessmentSource source, final ResolvedMigrationTargetMapping mapping,
			final Function<String, String> quote, final String batchSeparator,
			final Function<ResolvedMigrationTargetMapping.ColumnMapping, String> identityClause,
			final Predicate<String> usableSourceName,
			final Function<ResolvedMigrationTargetMapping.TableMapping, String> identityLoadGuidance) {
		final var sourceTables = new HashMap<ObjectId, Table>();
		for (final var schema : source.schemas()) {
			for (final var table : schema.getTables()) { sourceTables.put(tableId(table), table); }
		}
		final var mappedTables = new HashMap<ObjectId, ResolvedMigrationTargetMapping.TableMapping>();
		mapping.tables().forEach(table -> mappedTables.put(table.sourceTable(), table));
		final var names = new NameRegistry(usableSourceName);
		final var omittedKeysAndIndexes = new java.util.ArrayList<String>();
		final var omittedForeignKeys = new java.util.ArrayList<String>();
		final var keySql = new StringBuilder();
		final var indexSql = new StringBuilder();
		final var foreignKeySql = new StringBuilder();
		int emittedPrimaryKeys = 0;
		int emittedUniqueConstraints = 0;
		int emittedChecks = 0;
		int emittedIndexes = 0;
		int emittedForeignKeys = 0;
		final var sql = new StringBuilder("-- Review-only DDL. Keys and secondary indexes are emitted only when every participating object is mapped.\n")
				.append("-- Source defaults are not copied automatically; only reviewed target defaults from the mapping are emitted.\n")
				.append("-- Nullable unique keys, conversion expressions and cascade rules are not included.\n")
				.append(mappingSummaryComments(source, mapping, mappedTables))
				.append("\n-- sqlapp:phase-1:begin\n-- Phase 1: Create target tables.\n")
				.append(phaseOneScopeGuidance(source, mappedTables));
		for (final var mapped : mapping.tables()) {
			final Table sourceTable = sourceTables.get(mapped.sourceTable());
			final Map<String, String> columns = columns(mapped);
			final String primaryKey = primaryKey(sourceTable, columns, quote);
			if (primaryKey == null && hasPrimaryKey(sourceTable)) {
				omittedKeysAndIndexes.add(omittedObject(sourceTable, "PRIMARY KEY",
						sourceTable.getConstraints().getPrimaryKeyConstraint().getName(),
						"one or more participating columns are not mapped"));
			}
			final var uniqueKeys = uniqueKeys(sourceTable, mapped, columns, quote, omittedKeysAndIndexes);
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
				if (i + 1 < mapped.columns().size() || !checks.isEmpty()) { sql.append(','); }
				sql.append('\n');
			}
			final var constraints = new java.util.ArrayList<String>();
			if (primaryKey != null) {
				emittedPrimaryKeys++;
				final String sourceName = sourceTable.getConstraints().getPrimaryKeyConstraint().getName();
				keySql.append("ALTER TABLE ").append(name(mapped, quote)).append(" ADD CONSTRAINT ")
						.append(quote.apply(names.choose(mapped.targetSchema(), "PK", sourceName,
								objectIdentity(mapped) + ".primary")))
						.append(" PRIMARY KEY (").append(primaryKey).append(");\n").append(batchSeparator);
			}
			emittedUniqueConstraints += uniqueKeys.size();
			emittedChecks += checks.size();
			for (int i = 0; i < uniqueKeys.size(); i++) {
				final var unique = uniqueKeys.get(i);
				keySql.append("ALTER TABLE ").append(name(mapped, quote)).append(" ADD CONSTRAINT ")
						.append(quote.apply(names.choose(mapped.targetSchema(), "UK", unique.sourceName(),
								objectIdentity(mapped) + ".unique." + i)))
						.append(" UNIQUE (").append(unique.columns()).append(");\n").append(batchSeparator);
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
				if (Boolean.TRUE.equals(index.getSpecifics().get("IGNORE_NULLS", Boolean.class))) {
					omittedKeysAndIndexes.add(omittedObject(sourceTable, "INDEX", index.getName(),
							"Access IgnoreNulls semantics require target-specific index design"));
					continue;
				}
				if (index.isUnique()) {
					omittedKeysAndIndexes.add(omittedObject(sourceTable, "UNIQUE INDEX", index.getName(),
							"source unique index requires target-specific NULL and uniqueness design"));
					continue;
				}
				if (index.getColumns().isEmpty()) {
					omittedKeysAndIndexes.add(omittedObject(sourceTable, "INDEX", index.getName(), "source index has no columns"));
					continue;
				}
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
					emittedIndexes++;
					final String identity = objectIdentity(mapped) + "."
							+ index.getName() + "." + ordinal;
					indexSql.append("CREATE INDEX ").append(quote.apply(names.choose(mapped.targetSchema(), "IX", index.getName(), identity))).append(" ON ")
							.append(name(mapped, quote)).append(" (").append(keyColumns).append(");\n")
							.append(batchSeparator);
				}
				else {
					omittedKeysAndIndexes.add(omittedObject(sourceTable, "INDEX", index.getName(),
							"one or more participating columns are not mapped"));
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
				if (parent == null) {
					omittedForeignKeys.add(omittedForeignKey(sourceTable, foreignKey, "referenced table is not mapped"));
					continue;
				}
				if (foreignKey.getColumns().size() != foreignKey.getRelatedColumns().size()) {
					omittedForeignKeys.add(omittedForeignKey(sourceTable, foreignKey, "source relationship columns are inconsistent"));
					continue;
				}
				final Map<String, String> parentColumns = columns(parent);
				if (!referencedKeyEmitted(foreignKey, related, parent, parentColumns)) {
					omittedForeignKeys.add(omittedForeignKey(sourceTable, foreignKey, "referenced primary or unique key is not emitted"));
					continue;
				}
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
					emittedForeignKeys++;
					final String identity = objectIdentity(mapped) + ".foreign." + ordinal + "." + objectIdentity(parent);
					foreignKeySql.append("ALTER TABLE ").append(name(mapped, quote)).append(" ADD CONSTRAINT ")
							.append(quote.apply(names.choose(mapped.targetSchema(), "FK", foreignKey.getName(), identity))).append(" FOREIGN KEY (")
							.append(child).append(") REFERENCES ").append(name(parent, quote)).append(" (")
							.append(referenced).append(");\n").append(batchSeparator);
				}
				else { omittedForeignKeys.add(omittedForeignKey(sourceTable, foreignKey, "one or more participating columns are not mapped")); }
			}
		}
		return sql.append("-- sqlapp:phase-1:end\n")
				.append(ddlObjectSummaryComments(mapping.tables().size(), emittedPrimaryKeys,
				emittedUniqueConstraints, emittedChecks, emittedIndexes, emittedForeignKeys,
				omittedKeysAndIndexes.size(), omittedForeignKeys.size()))
				.append("\n-- sqlapp:phase-2:begin\n-- Phase 2: Load data in the suggested order and run verification queries.\n")
				.append(loadOrderComments(sourceTables, mapping, mappedTables, identityLoadGuidance, quote))
				.append(rowCountBaselineComments(source, mapping, quote))
				.append(integrityVerificationComments(sourceTables, mapping, mappedTables, quote))
				.append(phaseTwoCompletionGateComments(sourceTables, mapping,
						emittedPrimaryKeys + emittedUniqueConstraints + emittedForeignKeys > 0))
				.append("-- sqlapp:phase-2:end\n")
				.append("\n-- sqlapp:phase-3:begin\n-- Phase 3: After loading and verifying data, create keys and secondary indexes.\n")
				.append(phaseThreeObjectGuidance(omittedKeysAndIndexes))
				.append(keySql)
				.append(indexSql)
				.append("-- sqlapp:phase-3:end\n")
				.append("\n-- sqlapp:phase-4:begin\n-- Phase 4: After loading and verifying data, apply foreign keys.\n")
				.append(phaseFourRelationshipGuidance(sourceTables, mapping, omittedForeignKeys))
				.append(foreignKeySql)
				.append("-- sqlapp:phase-4:end\n")
				.append("\n-- sqlapp:appendix:begin\n")
				.append(omittedKeyAndIndexComments(omittedKeysAndIndexes))
				.append(omittedForeignKeyComments(omittedForeignKeys))
				.append(omittedSourceObjectComments(source, mappedTables))
				.append(sourceTableAndColumnMappingComments(mapping))
				.append(sourceDescriptionComments(sourceTables, mapping))
				.append(sourceColumnTypeMappingComments(sourceTables, mapping))
				.append(columnSemanticReviewComments(sourceTables, mapping))
				.append(sourceValidationReviewComments(sourceTables, mapping))
				.append(sourceRelationshipActionReviewComments(sourceTables, mapping))
				.append(names.mappingComments())
				.append(names.fallbackComments())
				.append("-- sqlapp:appendix:end\n").toString();
	}

	private static String phaseTwoCompletionGateComments(final Map<ObjectId, Table> sourceTables,
			final ResolvedMigrationTargetMapping mapping, final boolean hasIntegrityVerification) {
		final boolean hasIdentity = mapping.tables().stream().flatMap(table -> table.columns().stream())
				.anyMatch(column -> Boolean.TRUE.equals(column.identity()));
		boolean hasCalculated = false;
		boolean hasValidationOrEmptyStringPolicy = false;
		boolean hasDefaultDifference = false;
		boolean hasConversion = false;
		for (final var mapped : mapping.tables()) {
			final Table sourceTable = sourceTables.get(mapped.sourceTable());
			if (sourceTable == null) { continue; }
			hasValidationOrEmptyStringPolicy |= sourceTable.getConstraints().stream()
					.anyMatch(constraint -> constraint instanceof com.sqlapp.data.schemas.CheckConstraint);
			for (final var mappedColumn : mapped.columns()) {
				hasConversion |= normalizedExpression(mappedColumn.conversion()) != null;
				final var sourceColumn = sourceColumn(sourceTable, mappedColumn.sourceColumn().name());
				if (sourceColumn == null) { continue; }
				hasCalculated |= normalizedExpression(sourceColumn.getFormula()) != null;
				hasDefaultDifference |= !java.util.Objects.equals(normalizedExpression(sourceColumn.getDefaultValue()),
						normalizedExpression(mappedColumn.defaultExpression()));
				hasValidationOrEmptyStringPolicy |= normalizedExpression(sourceColumn.getCheck()) != null
						|| Boolean.TRUE.equals(sourceColumn.getSpecifics().get("access.allowZeroLength", Boolean.class));
			}
		}
		return "\n-- Phase 2 completion gate (do not continue to Phase 3 until every applicable check passes):\n"
				+ "-- - Target row counts and available null-count/value-range baselines match the Access source.\n"
				+ (hasIntegrityVerification ? "-- - Every generated duplicate query returns no rows and every generated orphan count is 0.\n" : "")
				+ (hasIdentity ? "-- - Every Access AutoNumber maximum is preserved and the target identity generator or seed is ready for new inserts.\n" : "")
				+ (hasDefaultDifference ? "-- - Every changed or omitted Access default produces the approved value for target-side inserts.\n" : "")
				+ (hasConversion ? "-- - Every mapped conversion matches approved representative, boundary and NULL source values.\n" : "")
				+ (hasCalculated ? "-- - Every Access calculated field matches the approved materialization or target recalculation behavior.\n" : "")
				+ (hasValidationOrEmptyStringPolicy ? "-- - Translated Access validation and empty-string behavior passes representative insert and update tests.\n" : "")
				+ "-- Record reviewed exceptions explicitly before continuing.\n";
	}

	private static String phaseOneScopeGuidance(final MigrationAssessmentSource source,
			final Map<ObjectId, ResolvedMigrationTargetMapping.TableMapping> mappedTables) {
		for (final var schema : source.schemas()) {
			for (final var table : schema.getTables()) {
				final var mapped = mappedTables.get(tableId(table));
				if (mapped == null) {
					return "-- Before executing Phase 1, approve every Access table and field omitted from the migration scope and listed in the appendix.\n";
				}
				final var mappedColumns = new java.util.HashSet<String>();
				mapped.columns().forEach(column -> mappedColumns.add(key(column.sourceColumn().name())));
				if (table.getColumns().stream().anyMatch(column -> !mappedColumns.contains(key(column.getName())))) {
					return "-- Before executing Phase 1, approve every Access table and field omitted from the migration scope and listed in the appendix.\n";
				}
			}
		}
		return "";
	}

	private static String phaseThreeObjectGuidance(final java.util.List<String> omittedKeysAndIndexes) {
		return omittedKeysAndIndexes.isEmpty() ? ""
				: "-- Before executing Phase 3, approve the replacement or exclusion of every Access key and index omitted in the appendix.\n";
	}

	private static String phaseFourRelationshipGuidance(final Map<ObjectId, Table> sourceTables,
			final ResolvedMigrationTargetMapping mapping, final java.util.List<String> omittedForeignKeys) {
		final var guidance = new StringBuilder();
		if (!omittedForeignKeys.isEmpty()) {
			guidance.append("-- Before executing Phase 4, approve the replacement or exclusion of every Access relationship omitted in the appendix.\n");
		}
		for (final var mapped : mapping.tables()) {
			final Table table = sourceTables.get(mapped.sourceTable());
			if (table == null) { continue; }
			for (final var foreignKey : table.getConstraints().getForeignKeyConstraints()) {
				if (foreignKey.getUpdateRule() == com.sqlapp.data.schemas.CascadeRule.Cascade
						|| foreignKey.getDeleteRule() == com.sqlapp.data.schemas.CascadeRule.Cascade) {
					guidance.append("-- Before executing Phase 4, approve the target-specific replacement for every Access cascade action listed in the appendix.\n");
					return guidance.toString();
				}
			}
		}
		return guidance.toString();
	}

	private static String ddlObjectSummaryComments(final int tables, final int primaryKeys,
			final int uniqueConstraints, final int checks, final int indexes, final int foreignKeys,
			final int omittedKeysAndIndexes, final int omittedForeignKeys) {
		return "\n-- Target DDL object summary:\n"
				+ "-- tables: " + tables + "; primary keys: " + primaryKeys
				+ "; unique constraints: " + uniqueConstraints + "; checks: " + checks + "\n"
				+ "-- indexes: " + indexes + "; foreign keys: " + foreignKeys
				+ "; omitted keys/indexes: " + omittedKeysAndIndexes
				+ "; omitted foreign keys: " + omittedForeignKeys + "\n";
	}

	private static String mappingSummaryComments(final MigrationAssessmentSource source,
			final ResolvedMigrationTargetMapping mapping,
			final Map<ObjectId, ResolvedMigrationTargetMapping.TableMapping> mappedTables) {
		int sourceTables = 0;
		int sourceColumns = 0;
		int omittedTables = 0;
		int omittedColumns = 0;
		for (final var schema : source.schemas()) {
			for (final var table : schema.getTables()) {
				sourceTables++;
				sourceColumns += table.getColumns().size();
				final var mapped = mappedTables.get(tableId(table));
				if (mapped == null) {
					omittedTables++;
					continue;
				}
				final var mappedColumns = new java.util.HashSet<String>();
				mapped.columns().forEach(column -> mappedColumns.add(key(column.sourceColumn().name())));
				for (final var column : table.getColumns()) {
					if (!mappedColumns.contains(key(column.getName()))) { omittedColumns++; }
				}
			}
		}
		final int mappedColumns = mapping.tables().stream().mapToInt(table -> table.columns().size()).sum();
		return "-- Mapping summary:\n"
				+ "-- source tables: " + sourceTables + "; mapped tables: " + mapping.tables().size()
				+ "; omitted tables: " + omittedTables + "\n"
				+ "-- source columns: " + sourceColumns + "; mapped columns: " + mappedColumns
				+ "; omitted columns in mapped tables: " + omittedColumns + "\n"
				+ "-- data profile scanned: " + source.dataScanned()
				+ "; relationships collected: " + source.relationshipsCollected() + "\n";
	}

	private static String omittedSourceObjectComments(final MigrationAssessmentSource source,
			final Map<ObjectId, ResolvedMigrationTargetMapping.TableMapping> mappedTables) {
		final var omissions = new java.util.ArrayList<String>();
		for (final var schema : source.schemas()) {
			for (final var table : schema.getTables()) {
				final var mapped = mappedTables.get(tableId(table));
				if (mapped == null) {
					omissions.add("-- TABLE " + NameRegistry.commentValue(qualified(table.getCatalogName(),
							table.getSchemaName(), table.getName())) + ": not mapped\n");
					continue;
				}
				final var mappedColumns = new java.util.HashSet<String>();
				mapped.columns().forEach(column -> mappedColumns.add(key(column.sourceColumn().name())));
				for (final var column : table.getColumns()) {
					if (!mappedColumns.contains(key(column.getName()))) {
						omissions.add("-- COLUMN " + NameRegistry.commentValue(qualified(table.getCatalogName(),
								table.getSchemaName(), table.getName(), column.getName())) + ": not mapped\n");
					}
				}
			}
		}
		return omissions.isEmpty() ? ""
				: "\n-- Source tables and columns omitted from the target DDL:\n" + String.join("", omissions);
	}

	private static String sourceTableAndColumnMappingComments(final ResolvedMigrationTargetMapping mapping) {
		if (mapping.tables().isEmpty()) { return ""; }
		final var sql = new StringBuilder("\n-- Source table and column name mapping:\n");
		for (final var table : mapping.tables()) {
			final String sourceTable = qualified(table.sourceTable().catalog(), table.sourceTable().schema(),
					table.sourceTable().name());
			final String targetTable = qualified(null, table.targetSchema(), table.targetTable());
			sql.append("-- TABLE ").append(NameRegistry.commentValue(sourceTable)).append(" -> ")
					.append(NameRegistry.commentValue(targetTable)).append('\n');
			for (final var column : table.columns()) {
				final String sourceColumn = qualified(column.sourceColumn().catalog(), column.sourceColumn().schema(),
						column.sourceColumn().table() == null ? table.sourceTable().name() : column.sourceColumn().table(),
						column.sourceColumn().name());
				final String targetColumn = qualified(null, table.targetSchema(), table.targetTable(), column.targetColumn());
				sql.append("-- COLUMN ").append(NameRegistry.commentValue(sourceColumn)).append(" -> ")
						.append(NameRegistry.commentValue(targetColumn)).append('\n');
			}
		}
		return sql.toString();
	}

	private static String sourceColumnTypeMappingComments(final Map<ObjectId, Table> sourceTables,
			final ResolvedMigrationTargetMapping mapping) {
		if (mapping.tables().isEmpty()) { return ""; }
		final var sql = new StringBuilder("\n-- Source-to-target column type mapping:\n");
		for (final var table : mapping.tables()) {
			final Table sourceTable = sourceTables.get(table.sourceTable());
			for (final var column : table.columns()) {
				final var sourceColumn = sourceColumn(sourceTable, column.sourceColumn().name());
				final String sourceName = qualified(column.sourceColumn().catalog(), column.sourceColumn().schema(),
						column.sourceColumn().table() == null ? table.sourceTable().name() : column.sourceColumn().table(),
						column.sourceColumn().name());
				final String targetName = qualified(null, table.targetSchema(), table.targetTable(), column.targetColumn());
				sql.append("-- ").append(NameRegistry.commentValue(sourceName)).append(" (")
						.append(NameRegistry.commentValue(sourceType(sourceColumn))).append(") -> ")
						.append(NameRegistry.commentValue(targetName)).append(" (")
						.append(NameRegistry.commentValue(column.targetType())).append(')');
				if (column.conversion() != null && !column.conversion().isBlank()) {
					sql.append("; conversion ").append(NameRegistry.commentValue(column.conversion()));
				}
				sql.append("; nullability ").append(sourceColumn == null ? "<unknown>"
						: sourceColumn.isNotNull() ? "required" : "nullable")
						.append(" -> ").append(targetNullability(sourceTable, column))
						.append("; identity ").append(sourceColumn == null ? "<unknown>" : sourceColumn.isIdentity())
						.append(" -> ").append(column.identity() == null ? "unspecified" : column.identity())
						.append("; default ").append(commentExpression(sourceColumn == null ? null : sourceColumn.getDefaultValue()))
						.append(" -> ").append(commentExpression(column.defaultExpression()));
				if (sourceColumn != null && Boolean.TRUE.equals(
						sourceColumn.getSpecifics().get("access.allowZeroLength", Boolean.class))) {
					sql.append("; Access AllowZeroLength true -> target validation unspecified");
				}
				if (sourceColumn != null && normalizedExpression(sourceColumn.getFormula()) != null) {
					sql.append("; calculated ").append(commentExpression(sourceColumn.getFormula()))
							.append(" -> materialized target column");
				}
				sql.append('\n');
			}
		}
		return sql.toString();
	}

	private static String columnSemanticReviewComments(final Map<ObjectId, Table> sourceTables,
			final ResolvedMigrationTargetMapping mapping) {
		final var reviews = new java.util.ArrayList<String>();
		for (final var table : mapping.tables()) {
			final Table sourceTable = sourceTables.get(table.sourceTable());
			for (final var column : table.columns()) {
				final var sourceColumn = sourceColumn(sourceTable, column.sourceColumn().name());
				if (sourceColumn == null) { continue; }
				final String sourceName = qualified(column.sourceColumn().catalog(), column.sourceColumn().schema(),
						column.sourceColumn().table() == null ? table.sourceTable().name() : column.sourceColumn().table(),
						column.sourceColumn().name());
				final String targetNullability = targetNullability(sourceTable, column);
				final String sourceNullability = sourceColumn.isNotNull() ? "required" : "nullable";
				if ((!"unspecified".equals(targetNullability) && !sourceNullability.equals(targetNullability))
						|| (sourceColumn.isNotNull() && "unspecified".equals(targetNullability))) {
					reviews.add(semanticReview(sourceName, "nullability", sourceNullability, targetNullability));
				}
				if (sourceColumn.isIdentity() != Boolean.TRUE.equals(column.identity())) {
					reviews.add(semanticReview(sourceName, "identity", Boolean.toString(sourceColumn.isIdentity()),
							column.identity() == null ? "unspecified" : column.identity().toString()));
				}
				final String sourceDefault = normalizedExpression(sourceColumn.getDefaultValue());
				final String targetDefault = normalizedExpression(column.defaultExpression());
				if (!java.util.Objects.equals(sourceDefault, targetDefault)) {
					reviews.add(semanticReview(sourceName, "default", commentExpression(sourceDefault),
							commentExpression(targetDefault)));
				}
				if (Boolean.TRUE.equals(sourceColumn.getSpecifics().get("access.allowZeroLength", Boolean.class))) {
					reviews.add(semanticReview(sourceName, "empty-string policy", "Access AllowZeroLength=true",
							"target validation unspecified"));
				}
				final String sourceFormula = normalizedExpression(sourceColumn.getFormula());
				if (sourceFormula != null) {
					reviews.add(semanticReview(sourceName, "calculated expression", commentExpression(sourceFormula),
							"materialized; load conversion=" + commentExpression(column.conversion())));
				}
			}
		}
		return reviews.isEmpty() ? ""
				: "\n-- Column semantic differences requiring review:\n" + String.join("", reviews);
	}

	private static String semanticReview(final String sourceName, final String property,
			final String sourceValue, final String targetValue) {
		return "-- " + NameRegistry.commentValue(sourceName) + ": " + property + " "
				+ sourceValue + " -> " + targetValue + "\n";
	}

	private static String sourceValidationReviewComments(final Map<ObjectId, Table> sourceTables,
			final ResolvedMigrationTargetMapping mapping) {
		final var reviews = new java.util.ArrayList<String>();
		for (final var mapped : mapping.tables()) {
			final Table table = sourceTables.get(mapped.sourceTable());
			if (table == null) { continue; }
			final String tableName = qualified(mapped.sourceTable().catalog(), mapped.sourceTable().schema(),
					mapped.sourceTable().name());
			for (final var constraint : table.getConstraints()) {
				if (constraint instanceof com.sqlapp.data.schemas.CheckConstraint check
						&& normalizedExpression(check.getExpression()) != null) {
					reviews.add("-- TABLE " + NameRegistry.commentValue(tableName) + ": "
							+ commentExpression(check.getExpression()) + "\n");
				}
			}
			for (final var column : mapped.columns()) {
				final var sourceColumn = sourceColumn(table, column.sourceColumn().name());
				if (sourceColumn != null && normalizedExpression(sourceColumn.getCheck()) != null) {
					reviews.add("-- COLUMN " + NameRegistry.commentValue(tableName + "." + sourceColumn.getName())
							+ ": " + commentExpression(sourceColumn.getCheck()) + "\n");
				}
			}
		}
		return reviews.isEmpty() ? "" : "\n-- Access validation expressions requiring translation review; only mapped CHECK expressions are emitted:\n"
				+ String.join("", reviews);
	}

	private static String sourceRelationshipActionReviewComments(final Map<ObjectId, Table> sourceTables,
			final ResolvedMigrationTargetMapping mapping) {
		final var reviews = new java.util.ArrayList<String>();
		for (final var mapped : mapping.tables()) {
			final Table table = sourceTables.get(mapped.sourceTable());
			if (table == null) { continue; }
			for (final var foreignKey : table.getConstraints().getForeignKeyConstraints()) {
				if (foreignKey.getUpdateRule() != com.sqlapp.data.schemas.CascadeRule.Cascade
						&& foreignKey.getDeleteRule() != com.sqlapp.data.schemas.CascadeRule.Cascade) { continue; }
				reviews.add("-- FK " + NameRegistry.commentValue(table.getName() + "." + foreignKey.getName())
						+ ": update=" + foreignKey.getUpdateRule() + ", delete=" + foreignKey.getDeleteRule()
						+ " -> target cascade clauses omitted\n");
			}
		}
		return reviews.isEmpty() ? "" : "\n-- Access relationship actions requiring target-specific design:\n"
				+ String.join("", reviews);
	}

	private static String sourceDescriptionComments(final Map<ObjectId, Table> sourceTables,
			final ResolvedMigrationTargetMapping mapping) {
		final var descriptions = new java.util.ArrayList<String>();
		for (final var mapped : mapping.tables()) {
			final Table table = sourceTables.get(mapped.sourceTable());
			if (table == null) { continue; }
			final String tableName = qualified(mapped.sourceTable().catalog(), mapped.sourceTable().schema(),
					mapped.sourceTable().name());
			if (normalizedExpression(table.getRemarks()) != null) {
				descriptions.add("-- TABLE " + NameRegistry.commentValue(tableName) + ": "
						+ NameRegistry.commentValue(table.getRemarks()) + "\n");
			}
			for (final var mappedColumn : mapped.columns()) {
				final var column = sourceColumn(table, mappedColumn.sourceColumn().name());
				if (column != null && normalizedExpression(column.getRemarks()) != null) {
					descriptions.add("-- COLUMN " + NameRegistry.commentValue(tableName + "." + column.getName())
							+ ": " + NameRegistry.commentValue(column.getRemarks()) + "\n");
				}
			}
		}
		return descriptions.isEmpty() ? "" : "\n-- Access table and field descriptions retained for migration review:\n"
				+ String.join("", descriptions);
	}

	private static String normalizedExpression(final String expression) {
		return expression == null || expression.isBlank() ? null : expression.trim();
	}

	private static String targetNullability(final Table sourceTable,
			final ResolvedMigrationTargetMapping.ColumnMapping column) {
		if (sourceTable != null && sourceTable.getConstraints().getPrimaryKeyConstraint() != null) {
			for (final var primaryColumn : sourceTable.getConstraints().getPrimaryKeyConstraint().getColumns()) {
				if (key(primaryColumn.getName()).equals(key(column.sourceColumn().name()))) { return "required"; }
			}
		}
		if (column.nullable() == null) { return "unspecified"; }
		return column.nullable() ? "nullable" : "required";
	}

	private static String commentExpression(final String expression) {
		return expression == null || expression.isBlank() ? "<none>" : NameRegistry.commentValue(expression);
	}

	private static com.sqlapp.data.schemas.Column sourceColumn(final Table table, final String name) {
		if (table == null) { return null; }
		for (final var column : table.getColumns()) {
			if (key(column.getName()).equals(key(name))) { return column; }
		}
		return null;
	}

	private static String sourceType(final com.sqlapp.data.schemas.Column column) {
		if (column == null) { return "<unknown>"; }
		final String accessType = column.getSpecifics().get("access.sourceType", String.class);
		if (accessType != null && !accessType.isBlank()) { return accessType; }
		return column.getDataType() == null ? "<unknown>" : column.getDataType().toString();
	}

	private static String qualified(final String... parts) {
		final var name = new StringBuilder();
		for (final String part : parts) {
			if (part == null || part.isBlank()) { continue; }
			if (!name.isEmpty()) { name.append('.'); }
			name.append(part);
		}
		return name.toString();
	}

	private static String omittedObject(final Table table, final String kind, final String objectName,
			final String reason) {
		final String name = objectName == null || objectName.isBlank() ? "<blank>" : objectName;
		return "-- " + kind + " " + NameRegistry.commentValue(table.getName() + "." + name) + ": " + reason + "\n";
	}

	private static String omittedKeyAndIndexComments(final java.util.List<String> omissions) {
		return omissions.isEmpty() ? ""
				: "\n-- Source keys and indexes omitted from the target DDL:\n" + String.join("", omissions);
	}

	private static String omittedForeignKey(final Table table,
			final com.sqlapp.data.schemas.ForeignKeyConstraint foreignKey, final String reason) {
		final String name = foreignKey.getName() == null || foreignKey.getName().isBlank()
				? "<blank>" : foreignKey.getName();
		return "-- " + NameRegistry.commentValue(table.getName() + "." + name) + ": " + reason + "\n";
	}

	private static String omittedForeignKeyComments(final java.util.List<String> omissions) {
		return omissions.isEmpty() ? ""
				: "\n-- Source foreign keys omitted from the target DDL:\n" + String.join("", omissions);
	}

	private static String integrityVerificationComments(final Map<ObjectId, Table> sourceTables,
			final ResolvedMigrationTargetMapping mapping,
			final Map<ObjectId, ResolvedMigrationTargetMapping.TableMapping> mappedTables,
			final Function<String, String> quote) {
		final var sql = new StringBuilder("\n-- Post-load key integrity verification:\n");
		boolean emitted = false;
		for (final var mapped : mapping.tables()) {
			final Table sourceTable = sourceTables.get(mapped.sourceTable());
			if (sourceTable == null) { continue; }
			final Map<String, String> targetColumns = columns(mapped);
			final String primary = primaryKey(sourceTable, targetColumns, quote);
			if (primary != null) {
				emitted = true;
				duplicateQuery(sql, name(mapped, quote), primary);
			}
			for (final var unique : uniqueKeys(sourceTable, mapped, targetColumns, quote, null)) {
				emitted = true;
				duplicateQuery(sql, name(mapped, quote), unique.columns());
			}
			for (final var foreignKey : sourceTable.getConstraints().getForeignKeyConstraints()) {
				final Table related = foreignKey.getRelatedTable();
				final var parent = related == null ? null : mappedTables.get(tableId(related));
				if (parent == null || !foreignKeyComplete(foreignKey, targetColumns, columns(parent))
						|| !referencedKeyEmitted(foreignKey, related, parent, columns(parent))) { continue; }
				emitted = true;
				sql.append("-- Verify target orphans: SELECT COUNT(*) FROM ").append(name(mapped, quote))
						.append(" c LEFT JOIN ").append(name(parent, quote)).append(" p ON ");
				for (int i = 0; i < foreignKey.getColumns().size(); i++) {
					if (i > 0) { sql.append(" AND "); }
					sql.append("c.").append(quote.apply(targetColumns.get(key(foreignKey.getColumns().get(i).getName()))))
							.append(" = p.").append(quote.apply(columns(parent).get(key(foreignKey.getRelatedColumns().get(i).getName()))));
				}
				sql.append(" WHERE ");
				for (int i = 0; i < foreignKey.getColumns().size(); i++) {
					if (i > 0) { sql.append(" AND "); }
					sql.append("c.").append(quote.apply(targetColumns.get(key(foreignKey.getColumns().get(i).getName())))).append(" IS NOT NULL");
				}
				sql.append(" AND p.").append(quote.apply(columns(parent).get(key(foreignKey.getRelatedColumns().getFirst().getName()))))
						.append(" IS NULL;\n");
			}
		}
		if (!emitted) { sql.append("-- no fully mapped emitted keys\n"); }
		return sql.toString();
	}

	private static void duplicateQuery(final StringBuilder sql, final String table, final String columns) {
		sql.append("-- Verify target duplicates: SELECT ").append(columns).append(", COUNT(*) FROM ")
				.append(table).append(" GROUP BY ").append(columns).append(" HAVING COUNT(*) > 1;\n");
	}

	private static String rowCountBaselineComments(final MigrationAssessmentSource source,
			final ResolvedMigrationTargetMapping mapping, final Function<String, String> quote) {
		final var counts = new HashMap<ObjectId, Long>();
		if (source.dataProfile() != null) {
			source.dataProfile().tables().forEach(table -> counts.put(table.table(), table.rowCount()));
		}
		final var sql = new StringBuilder("\n-- Post-load row-count baseline from the Access source:\n");
		for (final var table : mapping.tables()) {
			sql.append("-- ").append(commentName(table)).append(": ");
			final Long count = counts.get(table.sourceTable());
			if (count == null) { sql.append("not scanned"); }
			else { sql.append(count).append(" rows"); }
			sql.append('\n').append("-- Verify target: SELECT COUNT(*) FROM ")
					.append(name(table, quote)).append(";\n");
		}
		sql.append("\n-- Post-load NULL-count baseline from the Access source:\n");
		final var profiles = new HashMap<ObjectId, MigrationDataProfile.ColumnProfile>();
		if (source.dataProfile() != null) {
			source.dataProfile().tables().forEach(table -> table.columns()
					.forEach(column -> profiles.put(column.column(), column)));
		}
		for (final var table : mapping.tables()) {
			for (final var column : table.columns()) {
				final var profile = profiles.get(column.sourceColumn());
				sql.append("-- ").append(commentName(table)).append('.').append(NameRegistry.commentValue(column.targetColumn()))
						.append(": source NULLs ");
				if (profile == null) { sql.append("not scanned"); }
				else if (profile.nullCount() == null) { sql.append("unavailable"); }
				else { sql.append(profile.nullCount()); }
				sql.append('\n').append("-- Verify target: SELECT COUNT(*) FROM ").append(name(table, quote))
						.append(" WHERE ").append(quote.apply(column.targetColumn())).append(" IS NULL;\n");
			}
		}
		sql.append("\n-- Post-load value-range baseline from the Access source:\n");
		boolean hasRanges = false;
		for (final var table : mapping.tables()) {
			for (final var column : table.columns()) {
				final var profile = profiles.get(column.sourceColumn());
				final String range = observedRange(profile);
				if (range == null) { continue; }
				hasRanges = true;
				sql.append("-- ").append(commentName(table)).append('.').append(NameRegistry.commentValue(column.targetColumn()))
						.append(": source ").append(range).append('\n')
						.append("-- Verify target: SELECT MIN(").append(quote.apply(column.targetColumn()))
						.append("), MAX(").append(quote.apply(column.targetColumn())).append(") FROM ")
						.append(name(table, quote)).append(";\n");
			}
		}
		if (!hasRanges) { sql.append("-- unavailable\n"); }
		return sql.toString();
	}

	private static String observedRange(final MigrationDataProfile.ColumnProfile profile) {
		if (profile == null) { return null; }
		if (profile.numeric() != null && (profile.numeric().minimum() != null || profile.numeric().maximum() != null)) {
			return "min=" + profile.numeric().minimum() + ", max=" + profile.numeric().maximum();
		}
		if (profile.dateTime() != null && (profile.dateTime().minimum() != null || profile.dateTime().maximum() != null)) {
			return "min=" + profile.dateTime().minimum() + ", max=" + profile.dateTime().maximum();
		}
		return null;
	}

	private static String loadOrderComments(final Map<ObjectId, Table> sourceTables,
			final ResolvedMigrationTargetMapping mapping,
			final Map<ObjectId, ResolvedMigrationTargetMapping.TableMapping> mappedTables,
			final Function<ResolvedMigrationTargetMapping.TableMapping, String> identityLoadGuidance,
			final Function<String, String> quote) {
		final var dependencies = new java.util.LinkedHashMap<ObjectId, java.util.Set<ObjectId>>();
		for (final var mapped : mapping.tables()) {
			final var parents = new java.util.LinkedHashSet<ObjectId>();
			final Table sourceTable = sourceTables.get(mapped.sourceTable());
			if (sourceTable != null) {
				final Map<String, String> childColumns = columns(mapped);
				for (final var foreignKey : sourceTable.getConstraints().getForeignKeyConstraints()) {
					final Table related = foreignKey.getRelatedTable();
					final var parent = related == null ? null : mappedTables.get(tableId(related));
					if (parent != null && !parent.sourceTable().equals(mapped.sourceTable())
							&& foreignKeyComplete(foreignKey, childColumns, columns(parent))
							&& referencedKeyEmitted(foreignKey, related, parent, columns(parent))) {
						parents.add(parent.sourceTable());
					}
				}
			}
			dependencies.put(mapped.sourceTable(), parents);
		}
		final var ordered = new java.util.ArrayList<ObjectId>();
		while (!dependencies.isEmpty()) {
			final var ready = dependencies.entrySet().stream().filter(entry -> entry.getValue().isEmpty())
					.map(Map.Entry::getKey).toList();
			if (ready.isEmpty()) { break; }
			ordered.addAll(ready);
			ready.forEach(dependencies::remove);
			dependencies.values().forEach(values -> values.removeAll(ready));
		}
		final var sql = new StringBuilder("\n-- Suggested data load order from emitted foreign keys:\n");
		for (int i = 0; i < ordered.size(); i++) {
			final var table = mappedTables.get(ordered.get(i));
			sql.append("-- ").append(i + 1).append(". ").append(commentName(table)).append('\n');
			appendIdentityLoadGuidance(sql, table, identityLoadGuidance, quote);
		}
		if (!dependencies.isEmpty()) {
			sql.append("-- Cyclic or cycle-dependent tables require staged loading or deferred constraints:\n");
			dependencies.keySet().forEach(id -> {
				final var table = mappedTables.get(id);
				sql.append("-- - ").append(commentName(table)).append('\n');
				appendIdentityLoadGuidance(sql, table, identityLoadGuidance, quote);
			});
		}
		return sql.toString();
	}

	private static void appendIdentityLoadGuidance(final StringBuilder sql,
			final ResolvedMigrationTargetMapping.TableMapping table,
			final Function<ResolvedMigrationTargetMapping.TableMapping, String> identityLoadGuidance,
			final Function<String, String> quote) {
		if (table.columns().stream().noneMatch(column -> Boolean.TRUE.equals(column.identity()))) { return; }
		final String guidance = identityLoadGuidance.apply(table);
		if (guidance != null && !guidance.isBlank()) { sql.append("--    ").append(guidance).append('\n'); }
		for (final var column : table.columns()) {
			if (!Boolean.TRUE.equals(column.identity())) { continue; }
			sql.append("--    Verify loaded Access AutoNumber maximum: SELECT MAX(")
					.append(quote.apply(column.targetColumn())).append(") FROM ")
					.append(name(table, quote)).append(";\n");
		}
	}

	private static boolean foreignKeyComplete(final com.sqlapp.data.schemas.ForeignKeyConstraint foreignKey,
			final Map<String, String> childColumns, final Map<String, String> parentColumns) {
		if (foreignKey.getColumns().size() != foreignKey.getRelatedColumns().size()) { return false; }
		for (int i = 0; i < foreignKey.getColumns().size(); i++) {
			if (!childColumns.containsKey(key(foreignKey.getColumns().get(i).getName()))
					|| !parentColumns.containsKey(key(foreignKey.getRelatedColumns().get(i).getName()))) { return false; }
		}
		return true;
	}

	private static boolean referencedKeyEmitted(final com.sqlapp.data.schemas.ForeignKeyConstraint foreignKey,
			final Table parentTable, final ResolvedMigrationTargetMapping.TableMapping parent,
			final Map<String, String> parentColumns) {
		if (parentTable == null || foreignKey.getRelatedColumns().isEmpty()) { return false; }
		final var primary = parentTable.getConstraints().getPrimaryKeyConstraint();
		if (primary != null && sameColumns(primary.getColumns(), foreignKey.getRelatedColumns())
				&& primary.getColumns().stream().allMatch(column -> parentColumns.containsKey(key(column.getName())))) {
			return true;
		}
		final var nullable = new HashMap<String, Boolean>();
		parent.columns().forEach(column -> nullable.put(key(column.sourceColumn().name()), column.nullable()));
		for (final var unique : parentTable.getConstraints().getUniqueConstraints()) {
			if (unique.isPrimaryKey() || !sameColumns(unique.getColumns(), foreignKey.getRelatedColumns())) { continue; }
			boolean emitted = true;
			for (final var column : unique.getColumns()) {
				final var sourceColumn = parentTable.getColumns().get(column.getName());
				if (!parentColumns.containsKey(key(column.getName())) || sourceColumn == null || !sourceColumn.isNotNull()
						|| !Boolean.FALSE.equals(nullable.get(key(column.getName())))) { emitted = false; break; }
			}
			if (emitted) { return true; }
		}
		return false;
	}

	private static boolean sameColumns(final java.util.List<? extends com.sqlapp.data.schemas.ReferenceColumn> left,
			final java.util.List<? extends com.sqlapp.data.schemas.ReferenceColumn> right) {
		if (left.size() != right.size()) { return false; }
		for (int i = 0; i < left.size(); i++) {
			if (!key(left.get(i).getName()).equals(key(right.get(i).getName()))) { return false; }
		}
		return true;
	}

	private static String commentName(final ResolvedMigrationTargetMapping.TableMapping table) {
		final String name = (table.targetSchema() == null || table.targetSchema().isBlank() ? "" : table.targetSchema() + ".")
				+ table.targetTable();
		return NameRegistry.commentValue(name);
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
	private static boolean hasPrimaryKey(final Table table) {
		return table != null && table.getConstraints().getPrimaryKeyConstraint() != null
				&& !table.getConstraints().getPrimaryKeyConstraint().getColumns().isEmpty();
	}
	private static java.util.Set<String> primaryKeyColumns(final Table table, final boolean omitted) {
		if (table == null || omitted || table.getConstraints().getPrimaryKeyConstraint() == null) { return java.util.Set.of(); }
		final var result = new java.util.HashSet<String>();
		table.getConstraints().getPrimaryKeyConstraint().getColumns().forEach(column -> result.add(key(column.getName())));
		return result;
	}
	private static java.util.List<Key> uniqueKeys(final Table table,
			final ResolvedMigrationTargetMapping.TableMapping mapped, final Map<String, String> columns,
			final Function<String, String> quote, final java.util.List<String> omissions) {
		if (table == null) { return java.util.List.of(); }
		final var nullable = new HashMap<String, Boolean>();
		mapped.columns().forEach(column -> nullable.put(key(column.sourceColumn().name()), column.nullable()));
		final var result = new java.util.ArrayList<Key>();
		for (final var unique : table.getConstraints().getUniqueConstraints()) {
			if (unique.isPrimaryKey()) { continue; }
			if (unique.getColumns().isEmpty()) {
				if (omissions != null) {
					omissions.add(omittedObject(table, "UNIQUE", unique.getName(), "source constraint has no columns"));
				}
				continue;
			}
			final var value = new StringBuilder();
			boolean complete = true;
			boolean required = true;
			for (int i = 0; i < unique.getColumns().size(); i++) {
				final String sourceName = unique.getColumns().get(i).getName();
				final String targetName = columns.get(key(sourceName));
				final var sourceColumn = table.getColumns().get(sourceName);
				if (targetName == null || sourceColumn == null) {
					complete = false;
					break;
				}
				if (!sourceColumn.isNotNull() || !Boolean.FALSE.equals(nullable.get(key(sourceName)))) { required = false; }
				if (i > 0) { value.append(", "); }
				value.append(quote.apply(targetName));
			}
			if (complete && required) { result.add(new Key(unique.getName(), value.toString())); }
			else if (omissions != null) {
				omissions.add(omittedObject(table, "UNIQUE", unique.getName(), complete
						? "source or target columns allow NULL"
						: "one or more participating columns are not mapped"));
			}
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
		private final java.util.List<NameMapping> mappings = new java.util.ArrayList<>();
		private final java.util.List<String> fallbacks = new java.util.ArrayList<>();
		private NameRegistry(final Predicate<String> usableSourceName) { this.usableSourceName = usableSourceName; }
		private String choose(final String schema, final String prefix, final String sourceName, final String identity) {
			if (sourceName != null && !sourceName.isBlank() && usableSourceName.test(sourceName)
					&& used.add((schema == null ? "" : key(schema)) + "." + key(sourceName))) {
				mappings.add(new NameMapping(prefix, sourceName, sourceName, true));
				return sourceName;
			}
			int salt = 0;
			while (true) {
				final String generated = generatedName(prefix, identity + (salt == 0 ? "" : "." + salt));
				if (used.add((schema == null ? "" : key(schema)) + "." + key(generated))) {
					mappings.add(new NameMapping(prefix, sourceName, generated, false));
					fallbacks.add("-- " + prefix + " source name " + commentValue(sourceName) + " -> " + generated + "\n");
					return generated;
				}
				salt++;
			}
		}
		private String mappingComments() {
			if (mappings.isEmpty()) { return ""; }
			final var sql = new StringBuilder("\n-- Source object name mapping:\n");
			for (final var mapping : mappings) {
				sql.append("-- ").append(mapping.kind()).append(' ')
						.append(commentValue(mapping.sourceName())).append(" -> ")
						.append(commentValue(mapping.targetName()))
						.append(mapping.retained() ? " (retained)\n" : " (generated)\n");
			}
			return sql.toString();
		}
		private String fallbackComments() {
			if (fallbacks.isEmpty()) { return ""; }
			return "\n-- Source object names replaced for target compatibility:\n" + String.join("", fallbacks);
		}
		private static String commentValue(final String value) {
			if (value == null || value.isBlank()) { return "<blank>"; }
			final var escaped = new StringBuilder("\"");
			value.codePoints().forEach(c -> {
				if (c == '\r') { escaped.append("\\r"); }
				else if (c == '\n') { escaped.append("\\n"); }
				else if (Character.isISOControl(c)) { escaped.append(String.format("\\u%04x", c)); }
				else if (c == '\\' || c == '"') { escaped.append('\\').appendCodePoint(c); }
				else { escaped.appendCodePoint(c); }
			});
			return escaped.append('"').toString();
		}
		private record NameMapping(String kind, String sourceName, String targetName, boolean retained) { }
	}
}
