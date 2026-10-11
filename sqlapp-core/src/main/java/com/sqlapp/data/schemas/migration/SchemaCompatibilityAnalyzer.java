/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas.migration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

/**
 * Classifies structural changes without relying on vendor-specific DDL syntax.
 * The expected schema is the migration contract; the actual schema is the live
 * target. Dialects may add stricter checks before execution.
 */
public final class SchemaCompatibilityAnalyzer {

	private SchemaCompatibilityAnalyzer() {
	}

	public static SchemaCompatibilityReport compare(final Schema expected, final Schema actual) {
		Objects.requireNonNull(expected, "expected");
		Objects.requireNonNull(actual, "actual");
		final List<SchemaCompatibilityChange> changes = new ArrayList<>();
		for (final Table expectedTable : expected.getTables()) {
			final Table actualTable = actual.getTables().get(expectedTable.getName());
			if (actualTable == null) {
				changes.add(change(SchemaCompatibility.BREAKING, expectedTable.getName(), "table", "present",
						"missing", "Required table is missing"));
				continue;
			}
			compareColumns(expectedTable, actualTable, changes);
			comparePrimaryKey(expectedTable, actualTable, changes);
		}
		for (final Table actualTable : actual.getTables()) {
			if (!expected.getTables().contains(actualTable.getName())) {
				changes.add(change(SchemaCompatibility.COMPATIBLE, actualTable.getName(), "table", "absent",
						"present", "Additional table does not break the migration contract"));
			}
		}
		SchemaCompatibility result = SchemaCompatibility.COMPATIBLE;
		for (final SchemaCompatibilityChange change : changes) {
			result = result.merge(change.compatibility());
		}
		return new SchemaCompatibilityReport(result, changes);
	}

	private static void compareColumns(final Table expectedTable, final Table actualTable,
			final List<SchemaCompatibilityChange> changes) {
		for (final Column expected : expectedTable.getColumns()) {
			final String id = expectedTable.getName() + "." + expected.getName();
			final Column actual = actualTable.getColumns().get(expected.getName());
			if (actual == null) {
				changes.add(change(SchemaCompatibility.BREAKING, id, "column", "present", "missing",
						"Required column is missing"));
				continue;
			}
			if (!Objects.equals(expected.getDataType(), actual.getDataType())) {
				changes.add(change(SchemaCompatibility.BREAKING, id, "dataType", text(expected.getDataType()),
						text(actual.getDataType()), "Column data type differs"));
			}
			if (expected.isIdentity() != actual.isIdentity()) {
				changes.add(change(SchemaCompatibility.BREAKING, id, "identity", Boolean.toString(expected.isIdentity()),
						Boolean.toString(actual.isIdentity()), "Identity generation differs"));
			} else if (expected.isIdentity() && !Objects.equals(expected.getIdentityGenerationType(),
					actual.getIdentityGenerationType())) {
				changes.add(change(SchemaCompatibility.BREAKING, id, "identityGenerationType",
						text(expected.getIdentityGenerationType()), text(actual.getIdentityGenerationType()),
						"Identity ALWAYS/BY DEFAULT behavior differs"));
			}
			if (!Objects.equals(expected.getDefaultValue(), actual.getDefaultValue())) {
				changes.add(change(SchemaCompatibility.CONDITIONAL, id, "defaultValue", expected.getDefaultValue(),
						actual.getDefaultValue(), "Column default expression differs"));
			}
			compareSize(id, "length", expected.getLength(), actual.getLength(), changes);
			compareSize(id, "scale", expected.getScale(), actual.getScale(), changes);
			if (expected.isNotNull() && !actual.isNotNull()) {
				changes.add(change(SchemaCompatibility.CONDITIONAL, id, "notNull", "true", "false",
						"The live target permits values rejected by the contract"));
			} else if (!expected.isNotNull() && actual.isNotNull()) {
				changes.add(change(SchemaCompatibility.BREAKING, id, "notNull", "false", "true",
						"The live target rejects null values permitted by the contract"));
			}
		}
		for (final Column actual : actualTable.getColumns()) {
			if (!expectedTable.getColumns().contains(actual.getName())) {
				final boolean generated = actual.isIdentity() || actual.getDefaultValue() != null;
				final SchemaCompatibility level = actual.isNotNull() && !generated ? SchemaCompatibility.BREAKING
						: SchemaCompatibility.COMPATIBLE;
				changes.add(change(level, actualTable.getName() + "." + actual.getName(), "column", "absent",
						"present", actual.isNotNull() && !generated ? "Additional mandatory column requires a supplied value"
								: generated ? "Additional generated column is compatible"
										: "Additional nullable column is compatible"));
			}
		}
	}

	private static void comparePrimaryKey(final Table expected, final Table actual,
			final List<SchemaCompatibilityChange> changes) {
		final List<String> expectedColumns = primaryKeyColumns(expected);
		final List<String> actualColumns = primaryKeyColumns(actual);
		if (!expectedColumns.equals(actualColumns)) {
			changes.add(change(SchemaCompatibility.BREAKING, expected.getName(), "primaryKey",
					expectedColumns.toString(), actualColumns.toString(), "Primary-key columns or order differ"));
		}
	}

	private static List<String> primaryKeyColumns(final Table table) {
		if (table.getPrimaryKeyConstraint() == null) {
			return List.of();
		}
		return table.getPrimaryKeyConstraint().getColumns().stream().map(column -> column.getName()).toList();
	}

	private static void compareSize(final String id, final String property, final Number expected, final Number actual,
			final List<SchemaCompatibilityChange> changes) {
		if (expected == null || actual == null || expected.longValue() == actual.longValue()) {
			return;
		}
		final SchemaCompatibility level = actual.longValue() >= expected.longValue() ? SchemaCompatibility.COMPATIBLE
				: SchemaCompatibility.BREAKING;
		changes.add(change(level, id, property, expected.toString(), actual.toString(),
				level == SchemaCompatibility.COMPATIBLE ? "Live target capacity is wider than the contract"
						: "Live target capacity is narrower than the contract"));
	}

	private static SchemaCompatibilityChange change(final SchemaCompatibility level, final String id,
			final String property, final String expected, final String actual, final String message) {
		return new SchemaCompatibilityChange(level, id, property, expected, actual, message);
	}

	private static String text(final Object value) {
		return value == null ? null : value.toString();
	}
}
