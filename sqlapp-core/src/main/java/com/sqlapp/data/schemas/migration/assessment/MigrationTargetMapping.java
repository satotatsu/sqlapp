/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.migration.assessment;

import java.util.List;

/** Small, user-authored target mapping for database migration assessment. */
public record MigrationTargetMapping(String format, int version, String sourceFingerprint,
		String targetDatabase, String targetVersion, List<TableMapping> tables) {
	public static final String FORMAT = "sqlapp-database-migration-mapping";
	public static final int CURRENT_VERSION = 1;
	public MigrationTargetMapping { tables = tables == null ? List.of() : List.copyOf(tables); }
	public record TableMapping(String sourceCatalog, String sourceSchema, String sourceTable,
			String targetSchema, String targetTable, List<String> checkExpressions, List<ColumnMapping> columns) {
		public TableMapping(final String sourceCatalog, final String sourceSchema, final String sourceTable,
				final String targetSchema, final String targetTable, final List<ColumnMapping> columns) {
			this(sourceCatalog, sourceSchema, sourceTable, targetSchema, targetTable, List.of(), columns);
		}
		public TableMapping {
			checkExpressions = checkExpressions == null ? List.of() : List.copyOf(checkExpressions);
			columns = columns == null ? List.of() : List.copyOf(columns);
		}
	}
	public record ColumnMapping(String sourceColumn, String targetColumn, String targetType,
			Boolean nullable, Boolean identity, String defaultExpression, String conversion) {
		public ColumnMapping(final String sourceColumn, final String targetColumn, final String targetType,
				final Boolean nullable, final Boolean identity, final String conversion) {
			this(sourceColumn, targetColumn, targetType, nullable, identity, null, conversion);
		}
		public ColumnMapping(final String sourceColumn, final String targetColumn, final String targetType,
				final Boolean nullable, final String conversion) {
			this(sourceColumn, targetColumn, targetType, nullable, null, null, conversion);
		}
	}
}
