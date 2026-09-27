/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.migration.assessment;

import java.util.List;
import java.util.Objects;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.ObjectId;

/** Validated mapping with canonical source identities retained in the report. */
public record ResolvedMigrationTargetMapping(String mappingFingerprint, String targetDatabase,
		String targetVersion, List<TableMapping> tables) {
	public ResolvedMigrationTargetMapping {
		Objects.requireNonNull(mappingFingerprint, "mappingFingerprint");
		tables = tables == null ? List.of() : List.copyOf(tables);
	}
	public record TableMapping(ObjectId sourceTable, String targetSchema, String targetTable,
			List<ColumnMapping> columns) {
		public TableMapping { Objects.requireNonNull(sourceTable, "sourceTable"); columns = List.copyOf(columns); }
	}
	public record ColumnMapping(ObjectId sourceColumn, String targetColumn, String targetType,
			Boolean nullable, String conversion) {
		public ColumnMapping { Objects.requireNonNull(sourceColumn, "sourceColumn"); }
	}
}
