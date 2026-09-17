/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
		requireEffectiveAtAfterCurrentRows(connection, dialect, table, definition, effectiveAt);
		requireRowInvariants(connection, dialect, table, definition);
		requireUniqueCurrentKeys(connection, dialect, table, definition);
	}

	static void afterMutation(final Connection connection, final Dialect dialect, final String table,
			final MigrationSnapshotDefinition definition) throws SQLException {
		requireRowInvariants(connection, dialect, table, definition);
		requireUniqueCurrentKeys(connection, dialect, table, definition);
	}

	private static void requireRowInvariants(final Connection connection, final Dialect dialect,
			final String table, final MigrationSnapshotDefinition definition) throws SQLException {
		if (definition.currentColumn() == null) return;
		final String validTo = dialect.quote(definition.validToColumn());
		final String current = dialect.quote(definition.currentColumn());
		final StringBuilder sql = new StringBuilder("SELECT 1 FROM ").append(table).append(" WHERE (")
				.append(validTo).append(" IS NULL AND (").append(current).append(" IS NULL OR ").append(current)
				.append(" <> ?)) OR (").append(validTo).append(" IS NOT NULL AND (").append(current)
				.append(" IS NULL OR ").append(current).append(" <> ?))");
		try (var statement = connection.prepareStatement(sql.toString())) {
			statement.setBoolean(1, true);
			statement.setBoolean(2, false);
			requireEmpty(statement, "Snapshot target violates temporal or current-marker invariants");
		}
	}

	private static void requireEffectiveAtAfterCurrentRows(final Connection connection, final Dialect dialect,
			final String table, final MigrationSnapshotDefinition definition, final Instant effectiveAt)
			throws SQLException {
		final String validFrom = dialect.quote(definition.validFromColumn());
		final String validTo = dialect.quote(definition.validToColumn());
		final String sql = "SELECT MAX(" + validFrom + ") FROM " + table + " WHERE " + validTo + " IS NULL";
		try (var statement = connection.createStatement(); var resultSet = statement.executeQuery(sql)) {
			if (resultSet.next()) {
				final Instant latest = timestamp(resultSet.getObject(1));
				if (latest != null && !latest.isBefore(effectiveAt)) {
					throw new SQLException("Snapshot target violates temporal or current-marker invariants");
				}
			}
		}
	}

	private static Instant timestamp(final Object value) throws SQLException {
		if (value == null) return null;
		if (value instanceof Instant instant) return instant;
		if (value instanceof Timestamp timestamp) return timestamp.toInstant();
		if (value instanceof java.util.Date date) return date.toInstant();
		if (value instanceof Number number) return Instant.ofEpochMilli(number.longValue());
		if (value instanceof CharSequence text) {
			final String string = text.toString();
			try { return Instant.parse(string); }
			catch (java.time.format.DateTimeParseException e) { /* try JDBC/local forms */ }
			try { return OffsetDateTime.parse(string).toInstant(); }
			catch (java.time.format.DateTimeParseException e) { /* try JDBC/local forms */ }
			try { return Timestamp.valueOf(string).toInstant(); }
			catch (IllegalArgumentException e) { /* try date-only form */ }
			try { return LocalDate.parse(string).atStartOfDay(ZoneId.systemDefault()).toInstant(); }
			catch (java.time.format.DateTimeParseException e) {
				throw new SQLException("Unsupported snapshot validity timestamp value: " + string, e);
			}
		}
		throw new SQLException("Unsupported snapshot validity timestamp type: " + value.getClass().getName());
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
				if (resultSet.next()) throw new SQLException("current target snapshot contains duplicate or null snapshot keys");
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
