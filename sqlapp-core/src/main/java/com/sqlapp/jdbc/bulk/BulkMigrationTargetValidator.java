/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.sqlapp.data.schemas.Table;

/** Validates target table and column identities before any migration write. */
public final class BulkMigrationTargetValidator {
	private BulkMigrationTargetValidator() {
	}

	public static void validate(final Connection connection, final BulkMigrationJobPlan plan) throws SQLException {
		java.util.Objects.requireNonNull(connection, "connection");
		java.util.Objects.requireNonNull(plan, "plan").validateUnchanged();
		for (final BulkMigrationJobTask task : plan.getTasks()) {
			validate(connection, task);
		}
	}

	private static void validate(final Connection connection, final BulkMigrationJobTask task) throws SQLException {
		final Table target = task.getEffectiveTargetTable();
		final List<Identity> matches = new ArrayList<>();
		try (ResultSet tables = connection.getMetaData().getTables(target.getCatalogName(), null, "%",
				new String[] { "TABLE" })) {
			while (tables.next()) {
				final Identity identity = new Identity(tables.getString("TABLE_CAT"), tables.getString("TABLE_SCHEM"),
						tables.getString("TABLE_NAME"));
				if (same(target.getName(), identity.name()) && optionalSame(target.getSchemaName(), identity.schema())
						&& optionalSame(target.getCatalogName(), identity.catalog())) {
					matches.add(identity);
				}
			}
		}
		if (matches.isEmpty()) {
			throw new SQLException("Bulk migration target table was not found for task " + task.getTaskId()
					+ ": " + qualified(target));
		}
		if (matches.size() > 1) {
			throw new SQLException("Bulk migration target table is ambiguous for task " + task.getTaskId()
					+ ": " + qualified(target));
		}
		final Identity identity = matches.getFirst();
		final Set<String> actual = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		final Set<String> nullable = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		try (ResultSet columns = connection.getMetaData().getColumns(identity.catalog(), identity.schema(),
				identity.name(), "%")) {
			while (columns.next()) {
				final String name = columns.getString("COLUMN_NAME");
				actual.add(name);
				if (columns.getInt("NULLABLE") != java.sql.DatabaseMetaData.columnNoNulls) {
					nullable.add(name);
				}
			}
		}
		final Set<String> missing = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		target.getColumns().forEach(column -> missing.add(column.getName()));
		missing.removeAll(actual);
		if (!missing.isEmpty()) {
			throw new SQLException("Bulk migration target table is missing columns for task " + task.getTaskId()
					+ ": " + missing);
		}
		validateKey(connection, task, identity, nullable);
		if (task.isRequireEmptyTarget()) {
			validateEmpty(connection, task);
		}
	}

	private static void validateEmpty(final Connection connection, final BulkMigrationJobTask task)
			throws SQLException {
		final List<String> keyColumns = targetKeyColumns(task);
		if (keyColumns.isEmpty()) {
			throw new SQLException("Bulk migration target emptiness cannot be checked without a key for task "
					+ task.getTaskId());
		}
		final JdbcBulkMigrationKeysetSource target = new JdbcBulkMigrationKeysetSource(connection,
				task.getEffectiveTargetTable(), keyColumns);
		final java.util.Iterator<com.sqlapp.data.schemas.Row> rows = target.iterator(null);
		Throwable failure = null;
		try {
			if (rows.hasNext()) {
				throw new SQLException("Bulk migration target must be empty for task " + task.getTaskId()
						+ ": " + qualified(task.getEffectiveTargetTable()));
			}
		} catch (SQLException | RuntimeException | Error e) {
			failure = e;
			throw e;
		} finally {
			BulkMigrationIteratorSupport.close(failure, rows);
		}
	}

	private static void validateKey(final Connection connection, final BulkMigrationJobTask task,
			final Identity identity, final Set<String> nullable) throws SQLException {
		if (task.getKeysetSource() == null) {
			return;
		}
		final List<String> keyColumns = targetKeyColumns(task);
		if (keyColumns.isEmpty()) {
			throw new SQLException("Bulk migration target key could not be resolved for task " + task.getTaskId());
		}
		final Set<String> key = names(keyColumns);
		final Set<String> nullableKeys = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		nullableKeys.addAll(key);
		nullableKeys.retainAll(nullable);
		if (!nullableKeys.isEmpty()) {
			throw new SQLException("Bulk migration target key columns must be NOT NULL for task "
					+ task.getTaskId() + ": " + nullableKeys);
		}
		final List<Set<String>> uniqueKeys = new ArrayList<>();
		final Set<String> primaryKey = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		try (ResultSet keys = connection.getMetaData().getPrimaryKeys(identity.catalog(), identity.schema(),
				identity.name())) {
			while (keys.next()) {
				primaryKey.add(keys.getString("COLUMN_NAME"));
			}
		}
		if (!primaryKey.isEmpty()) {
			uniqueKeys.add(primaryKey);
		}
		final Map<String, Set<String>> indexes = new LinkedHashMap<>();
		try (ResultSet values = connection.getMetaData().getIndexInfo(identity.catalog(), identity.schema(),
				identity.name(), true, false)) {
			while (values.next()) {
				final String name = values.getString("INDEX_NAME");
				final String column = values.getString("COLUMN_NAME");
				final String filter = values.getString("FILTER_CONDITION");
				if (name != null && column != null && !values.getBoolean("NON_UNIQUE")
						&& (filter == null || filter.isBlank())) {
					indexes.computeIfAbsent(name, ignored -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)).add(column);
				}
			}
		}
		uniqueKeys.addAll(indexes.values());
		if (uniqueKeys.stream().noneMatch(candidate -> candidate.size() == key.size() && candidate.containsAll(key))) {
			throw new SQLException("Bulk migration target key is not backed by a primary key or unique index for task "
					+ task.getTaskId() + ": " + keyColumns);
		}
	}

	private static List<String> targetKeyColumns(final BulkMigrationJobTask task) {
		if (task.getKeysetSource() instanceof JdbcBulkMigrationKeysetSource jdbc) {
			return jdbc.getKeyColumnNames().stream().map(task::getTargetColumnName).toList();
		} else {
			final Table target = task.getEffectiveTargetTable();
			return target.getPrimaryKeyConstraint() == null ? List.of()
					: target.getPrimaryKeyConstraint().getColumns().stream().map(column -> column.getName()).toList();
		}
	}

	private static Set<String> names(final List<String> values) {
		final Set<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		result.addAll(values);
		return result;
	}

	private static boolean optionalSame(final String expected, final String actual) {
		return expected == null || expected.isBlank() || same(expected, actual);
	}

	private static boolean same(final String expected, final String actual) {
		return expected != null && actual != null && expected.equalsIgnoreCase(actual);
	}

	private static String qualified(final Table table) {
		return java.util.stream.Stream.of(table.getCatalogName(), table.getSchemaName(), table.getName())
				.filter(value -> value != null && !value.isBlank()).collect(java.util.stream.Collectors.joining("."));
	}

	private record Identity(String catalog, String schema, String name) {
	}
}
