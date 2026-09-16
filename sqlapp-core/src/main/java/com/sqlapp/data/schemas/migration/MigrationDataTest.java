/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas.migration;

import java.util.List;
import java.util.Map;

/**
 * A migration invariant. Custom SQL is a normal sqlapp comment-template and
 * must return one row per failure.
 */
public record MigrationDataTest(String id, Type type, Severity severity, String catalogName, String schemaName,
		String tableName, List<String> columns, String sql, Map<String, Object> parameters, long warnIf,
		long errorIf, int maximumSamples) {

	public MigrationDataTest {
		if (id == null || id.isBlank()) {
			throw new IllegalArgumentException("id must not be empty");
		}
		type = type == null ? Type.CUSTOM_SQL : type;
		severity = severity == null ? Severity.ERROR : severity;
		columns = columns == null ? List.of() : List.copyOf(columns);
		parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
		if (type == Type.CUSTOM_SQL && (sql == null || sql.isBlank())) {
			throw new IllegalArgumentException("sql is required for CUSTOM_SQL: " + id);
		}
		if (warnIf < 0 || errorIf < 0 || maximumSamples < 0) {
			throw new IllegalArgumentException("thresholds and maximumSamples must not be negative: " + id);
		}
	}

	public enum Type {
		NOT_NULL, UNIQUE, RELATIONSHIPS, ACCEPTED_VALUES, CUSTOM_SQL
	}

	public enum Severity {
		WARN, ERROR
	}
}
