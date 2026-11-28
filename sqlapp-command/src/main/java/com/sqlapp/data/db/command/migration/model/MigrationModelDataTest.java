/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration.model;

import java.util.List;
import java.util.Objects;

/**
 * Declarative check of a built model. Every kind is a query for the rows that
 * break the rule; the test passes when it returns no rows.
 *
 * @param id         unique test name
 * @param model      tested model
 * @param kind       rule
 * @param columns    tested columns of NOT_NULL, UNIQUE, ACCEPTED_VALUES and
 *                   RELATIONSHIPS
 * @param values     accepted values of ACCEPTED_VALUES, as YAML scalars
 * @param to         model or source referenced by RELATIONSHIPS
 * @param fields     referenced columns of RELATIONSHIPS, matching
 *                   {@code columns}
 * @param expression condition of EXPRESSION that must be true for every row
 * @param min        minimum of ROW_COUNT, or null
 * @param max        maximum of ROW_COUNT, or null
 * @param sql        failing-rows query of SQL; it may use $ref and $source
 * @param severity   ERROR fails the run, WARN only reports
 */
public record MigrationModelDataTest(String id, String model, Kind kind, List<String> columns, List<Object> values,
		String to, List<String> fields, String expression, Long min, Long max, String sql, Severity severity) {
	public enum Kind {
		NOT_NULL, UNIQUE, ACCEPTED_VALUES, RELATIONSHIPS, EXPRESSION, ROW_COUNT, SQL
	}

	public enum Severity {
		ERROR, WARN
	}

	public MigrationModelDataTest {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(model, "model");
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(severity, "severity");
		columns = columns == null ? List.of() : List.copyOf(columns);
		values = values == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(values));
		fields = fields == null ? List.of() : List.copyOf(fields);
		switch (kind) {
		case NOT_NULL, UNIQUE -> require(!columns.isEmpty(), "needs at least one column");
		case ACCEPTED_VALUES -> {
			require(columns.size() == 1, "needs one column");
			require(!values.isEmpty(), "needs at least one value");
			require(values.stream()
					.allMatch(value -> value instanceof String || value instanceof Number || value instanceof Boolean),
					"accepts strings, numbers and booleans, not null");
		}
		case RELATIONSHIPS -> {
			require(!columns.isEmpty() && columns.size() == fields.size(),
					"needs columns and the same number of referenced fields");
			require(to != null && !to.isBlank(), "needs the referenced model or source in to");
		}
		case EXPRESSION -> require(expression != null && !expression.isBlank(), "needs an expression");
		case ROW_COUNT -> {
			require(min != null || max != null, "needs min or max");
			require(min == null || min >= 0, "min must not be negative");
			require(min == null || max == null || min <= max, "min must not exceed max");
		}
		case SQL -> require(sql != null && !sql.isBlank(), "needs a query");
		}
	}

	private void require(final boolean condition, final String message) {
		if (!condition) {
			throw new IllegalArgumentException("Data test " + id + " of model " + model + " " + message + ".");
		}
	}
}
