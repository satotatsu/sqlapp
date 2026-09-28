/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.assessment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;

import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.Evidence;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.Finding;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.Inventory;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.ObjectId;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.Severity;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessmentSource;
import com.sqlapp.data.schemas.migration.assessment.ResolvedMigrationTargetMapping;

/** Reports source objects omitted from an otherwise valid target mapping. */
final class MigrationTargetMappingCoverage {
	MigrationAssessment assess(final MigrationAssessmentSource source, final ResolvedMigrationTargetMapping mapping) {
		final var mappedTables = new HashSet<ObjectId>();
		final var mappedColumns = new HashSet<ObjectId>();
		final var mappingsByTable = new HashMap<ObjectId, ResolvedMigrationTargetMapping.TableMapping>();
		mapping.tables().forEach(table -> {
			mappedTables.add(table.sourceTable());
			mappingsByTable.put(table.sourceTable(), table);
			table.columns().forEach(column -> mappedColumns.add(column.sourceColumn()));
		});
		final var findings = new ArrayList<Finding>();
		int omittedTables = 0;
		int omittedColumns = 0;
		int semanticDifferences = 0;
		for (final var schema : source.schemas()) {
			for (final var table : schema.getTables()) {
				final var tableId = new ObjectId(schema.getCatalogName(), schema.getName(), "table", table.getName());
				if (!mappedTables.contains(tableId)) {
					omittedTables++;
					findings.add(new Finding("migration.mapping.unmapped-table", Severity.REVIEW, Evidence.SCHEMA,
							tableId, "The source table is not present in the target mapping.",
							"Add it to the mapping, or record that the table is intentionally outside the migration scope.", null));
					continue;
				}
				final var tableMapping = mappingsByTable.get(tableId);
				final boolean primaryComplete = primaryComplete(table, tableMapping);
				for (final var column : table.getColumns()) {
					final var columnId = new ObjectId(schema.getCatalogName(), schema.getName(), "column",
							column.getName(), table.getName());
					if (!mappedColumns.contains(columnId)) {
						omittedColumns++;
						findings.add(new Finding("migration.mapping.unmapped-column", Severity.REVIEW, Evidence.SCHEMA,
								columnId, "The source column is not present in the target mapping.",
								"Add it to the mapping, or record that the column is intentionally excluded or derived.", null));
						continue;
					}
					final var columnMapping = tableMapping.columns().stream()
							.filter(candidate -> candidate.sourceColumn().equals(columnId)).findFirst().orElseThrow();
					final String sourceNullability = column.isNotNull() ? "required" : "nullable";
					final String targetNullability = targetNullability(table, column.getName(), primaryComplete, columnMapping.nullable());
					if ((!"unspecified".equals(targetNullability) && !sourceNullability.equals(targetNullability))
							|| (column.isNotNull() && "unspecified".equals(targetNullability))) {
						semanticDifferences++;
						findings.add(semanticFinding("nullability-change", columnId, "nullability",
								sourceNullability, targetNullability));
					}
					if (column.isIdentity() != Boolean.TRUE.equals(columnMapping.identity())) {
						semanticDifferences++;
						findings.add(semanticFinding("identity-change", columnId, "identity",
								Boolean.toString(column.isIdentity()), columnMapping.identity() == null
										? "unspecified" : columnMapping.identity().toString()));
					}
					final String sourceDefault = expression(column.getDefaultValue());
					final String targetDefault = expression(columnMapping.defaultExpression());
					if (!Objects.equals(sourceDefault, targetDefault)) {
						semanticDifferences++;
						findings.add(semanticFinding("default-change", columnId, "default expression",
								value(sourceDefault), value(targetDefault)));
					}
				}
			}
		}
		return new MigrationAssessment(findings, java.util.List.of(
				new Inventory(null, "", "unmappedTables", omittedTables),
				new Inventory(null, "", "unmappedColumnsInMappedTables", omittedColumns),
				new Inventory(null, "", "mappingSemanticDifferences", semanticDifferences)));
	}

	private static boolean primaryComplete(final Table table,
			final ResolvedMigrationTargetMapping.TableMapping mapping) {
		final var primary = table.getConstraints().getPrimaryKeyConstraint();
		if (primary == null || primary.getColumns().isEmpty()) { return false; }
		final var mapped = new HashSet<String>();
		mapping.columns().forEach(column -> mapped.add(key(column.sourceColumn().name())));
		return primary.getColumns().stream().allMatch(column -> mapped.contains(key(column.getName())));
	}

	private static String targetNullability(final Table table, final String columnName,
			final boolean primaryComplete, final Boolean nullable) {
		if (primaryComplete && table.getConstraints().getPrimaryKeyConstraint().getColumns().stream()
				.anyMatch(column -> key(column.getName()).equals(key(columnName)))) { return "required"; }
		if (nullable == null) { return "unspecified"; }
		return nullable ? "nullable" : "required";
	}

	private static Finding semanticFinding(final String suffix, final ObjectId object, final String property,
			final String sourceValue, final String targetValue) {
		return new Finding("migration.mapping." + suffix, Severity.REVIEW, Evidence.SCHEMA, object,
				"Mapped column changes " + property + " from " + sourceValue + " to " + targetValue + ".",
				"Confirm the change is intentional and validate affected rows during a rehearsal migration.", null);
	}

	private static String expression(final String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static String value(final String value) { return value == null ? "<none>" : value; }
	private static String key(final String value) { return value.toUpperCase(Locale.ROOT); }
}
