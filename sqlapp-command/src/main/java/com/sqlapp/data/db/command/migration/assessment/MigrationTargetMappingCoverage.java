/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.assessment;

import java.util.ArrayList;
import java.util.HashSet;

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
		mapping.tables().forEach(table -> {
			mappedTables.add(table.sourceTable());
			table.columns().forEach(column -> mappedColumns.add(column.sourceColumn()));
		});
		final var findings = new ArrayList<Finding>();
		int omittedTables = 0;
		int omittedColumns = 0;
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
				for (final var column : table.getColumns()) {
					final var columnId = new ObjectId(schema.getCatalogName(), schema.getName(), "column",
							column.getName(), table.getName());
					if (!mappedColumns.contains(columnId)) {
						omittedColumns++;
						findings.add(new Finding("migration.mapping.unmapped-column", Severity.REVIEW, Evidence.SCHEMA,
								columnId, "The source column is not present in the target mapping.",
								"Add it to the mapping, or record that the column is intentionally excluded or derived.", null));
					}
				}
			}
		}
		return new MigrationAssessment(findings, java.util.List.of(
				new Inventory(null, "", "unmappedTables", omittedTables),
				new Inventory(null, "", "unmappedColumnsInMappedTables", omittedColumns)));
	}
}
