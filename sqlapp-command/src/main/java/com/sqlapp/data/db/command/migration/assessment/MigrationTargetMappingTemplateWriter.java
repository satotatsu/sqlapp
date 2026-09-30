/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.assessment;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;
import com.sqlapp.data.schemas.migration.assessment.DatabaseMigrationAssessmentProvider;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessmentSource;
import com.sqlapp.data.schemas.migration.assessment.MigrationTargetMapping;
import com.sqlapp.util.YamlConverter;

/** Writes a complete editable skeleton; unsupported types remain explicit null TODOs. */
final class MigrationTargetMappingTemplateWriter {
	void write(final File file, final String sourceFingerprint, final String targetDatabase, final String targetVersion,
			final MigrationAssessmentSource source, final DatabaseMigrationAssessmentProvider provider) throws IOException {
		final Map<String, Object> root = new LinkedHashMap<>();
		root.put("format", MigrationTargetMapping.FORMAT);
		root.put("version", MigrationTargetMapping.CURRENT_VERSION);
		root.put("sourceFingerprint", sourceFingerprint);
		root.put("targetDatabase", targetDatabase.toLowerCase(java.util.Locale.ROOT));
		root.put("targetVersion", targetVersion);
		final var tables = new ArrayList<Map<String, Object>>();
		for (final var schema : source.schemas()) {
			for (final var table : schema.getTables()) {
				final Map<String, Object> tableValue = new LinkedHashMap<>();
				if (schema.getCatalogName() != null && !schema.getCatalogName().isBlank()) { tableValue.put("sourceCatalog", schema.getCatalogName()); }
				if (schema.getName() != null && !schema.getName().isBlank()) { tableValue.put("sourceSchema", schema.getName()); }
				tableValue.put("sourceTable", table.getName());
				final var columns = new ArrayList<Map<String, Object>>();
				for (final var column : table.getColumns()) {
					final Map<String, Object> columnValue = new LinkedHashMap<>();
					columnValue.put("sourceColumn", column.getName());
					columnValue.put("targetType", provider.suggestTargetType(column, targetVersion));
					columnValue.put("nullable", column.isIdentity() ? false : !column.isNotNull());
					if (column.isIdentity()) { columnValue.put("identity", null); }
					columns.add(columnValue);
				}
				tableValue.put("columns", columns);
				tables.add(tableValue);
			}
		}
		root.put("tables", tables);
		final var converter = new YamlConverter();
		AtomicMigrationFile.write(file.toPath(), temporary -> converter.writeJsonValue(temporary.toFile(), root));
	}
}
