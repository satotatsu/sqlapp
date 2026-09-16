/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas.migration;

import java.time.Duration;
import java.util.Map;

/** Watermark SLA used to decide whether a migration is ready for cutover. */
public record MigrationFreshnessCheck(String id, String catalogName, String schemaName, String tableName,
		String watermarkColumn, String sourceSql, String targetSql, Map<String, Object> parameters, Duration maximumLag) {

	public MigrationFreshnessCheck {
		if (id == null || id.isBlank()) {
			throw new IllegalArgumentException("id must not be empty");
		}
		final boolean generated = tableName != null && !tableName.isBlank() && watermarkColumn != null
				&& !watermarkColumn.isBlank();
		final boolean custom = sourceSql != null && !sourceSql.isBlank() && targetSql != null && !targetSql.isBlank();
		if (generated == custom) {
			throw new IllegalArgumentException(
					"Specify either table/watermarkColumn or sourceSql/targetSql: " + id);
		}
		parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
		if (maximumLag == null || maximumLag.isNegative()) {
			throw new IllegalArgumentException("maximumLag must not be negative: " + id);
		}
	}
}
