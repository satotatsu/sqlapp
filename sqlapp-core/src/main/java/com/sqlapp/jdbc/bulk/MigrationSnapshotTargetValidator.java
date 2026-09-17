/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.migration.MigrationSnapshotDefinition;

/** Bounded-result, set-based validation of SCD2 target invariants. */
final class MigrationSnapshotTargetValidator {
	private MigrationSnapshotTargetValidator() {
	}

	static void beforeMutation(final Connection connection, final Dialect dialect, final String table,
			final MigrationSnapshotDefinition definition, final Instant effectiveAt) throws SQLException {
		requireRowInvariants(connection, dialect, table, definition, effectiveAt);
		requireUniqueCurrentKeys(connection, dialect, table, definition);
	}

	static void afterMutation(final Connection connection, final Dialect dialect, final String table,
			final MigrationSnapshotDefinition definition) throws SQLException {
		requireRowInvariants(connection, dialect, table, definition, null);
		requireUniqueCurrentKeys(connection, dialect, table, definition);
	}

	private static void requireRowInvariants(final Connection connection, final Dialect dialect,
			final String table, final MigrationSnapshotDefinition definition, final Instant effectiveAt)
			throws SQLException {
		final String validFrom = dialect.quote(definition.validFromColumn());
		final String validTo = dialect.quote(definition.validToColumn());
		final StringBuilder sql = new StringBuilder("SELECT 1 FROM ").append(table).append(" WHERE (")
				.append(validTo).append(" IS NOT NULL AND ").append(validTo).append(" <= ").append(validFrom)
				.append(")");
		if (effectiveAt != null) {
			sql.append(" OR (").append(validTo).append(" IS NULL AND ").append(validFrom).append(" >= ?)");
		}
		if (definition.currentColumn() != null) {
			final String current = dialect.quote(definition.currentColumn());
			sql.append(" OR (").append(validTo).append(" IS NULL AND (").append(current)
					.append(" IS NULL OR ").append(current).append(" <> ?)) OR (").append(validTo)
					.append(" IS NOT NULL AND (").append(current).append(" IS NULL OR ").append(current)
					.append(" <> ?))");
		}
		try (var statement = connection.prepareStatement(sql.toString())) {
			int parameter = 1;
			if (effectiveAt != null) statement.setTimestamp(parameter++, Timestamp.from(effectiveAt));
			if (definition.currentColumn() != null) {
				statement.setBoolean(parameter++, true);
				statement.setBoolean(parameter, false);
			}
			requireEmpty(statement, "Snapshot target violates temporal or current-marker invariants");
		}
	}

	private static void requireUniqueCurrentKeys(final Connection connection, final Dialect dialect,
			final String table, final MigrationSnapshotDefinition definition) throws SQLException {
		final List<String> keys = definition.keyColumns();
		final String keyList = keys.stream().map(dialect::quote).collect(Collectors.joining(", "));
		final String nullKey = keys.stream().map(x -> "COUNT(" + dialect.quote(x) + ") = 0")
				.collect(Collectors.joining(" OR "));
		final String sql = "SELECT 1 FROM " + table + " WHERE " + dialect.quote(definition.validToColumn())
				+ " IS NULL GROUP BY " + keyList + " HAVING COUNT(*) > 1 OR " + nullKey;
		try (var statement = connection.createStatement()) {
			statement.setMaxRows(1);
			try (var resultSet = statement.executeQuery(sql)) {
				if (resultSet.next()) throw new SQLException("Current snapshot target contains duplicate or null keys");
			}
		}
	}

	private static void requireEmpty(final java.sql.PreparedStatement statement, final String message)
			throws SQLException {
		statement.setMaxRows(1);
		try (var resultSet = statement.executeQuery()) {
			if (resultSet.next()) throw new SQLException(message);
		}
	}
}
