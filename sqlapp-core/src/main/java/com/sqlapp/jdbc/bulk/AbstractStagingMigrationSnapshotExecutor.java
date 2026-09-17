/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.MigrationSnapshotDefinition;

/** Shared staging, comparison and set-based DML workflow for SCD2 dialects. */
public abstract class AbstractStagingMigrationSnapshotExecutor implements SetBasedMigrationSnapshotExecutor {
	protected final Dialect dialect;

	protected AbstractStagingMigrationSnapshotExecutor(final Dialect dialect) {
		this.dialect = java.util.Objects.requireNonNull(dialect, "dialect");
	}

	protected abstract String createStageSql(String stage, String target, List<String> columns);
	protected abstract String updateTarget(String target);
	protected String updateFrom(final String target) { return ""; }

	protected String cleanupStageSql(final String stage) { return "DROP TABLE IF EXISTS " + stage; }
	protected String stageName() { return "SQLAPP_SCD2_" + UUID.randomUUID().toString().replace("-", ""); }
	protected String stageIdentifier(final String name) { return dialect.quote(name); }
	protected String valuesEqual(final String left, final String right) { return left + " IS NOT DISTINCT FROM " + right; }
	protected String valuesDifferent(final String left, final String right) { return left + " IS DISTINCT FROM " + right; }

	@Override
	public final MigrationSnapshotExecutionResult execute(final Connection connection, final Table table,
			final MigrationSnapshotDefinition definition, final Instant effectiveAt,
			final Iterable<? extends Map<String, Object>> sourceRows, final int batchSize) throws SQLException {
		return execute(connection, table, definition, effectiveAt, sourceRows, batchSize,
				MigrationSnapshotExecutionGuard.NO_OP);
	}

	@Override
	public final MigrationSnapshotExecutionResult execute(final Connection connection, final Table table,
			final MigrationSnapshotDefinition definition, final Instant effectiveAt,
			final Iterable<? extends Map<String, Object>> sourceRows, final int batchSize,
			final MigrationSnapshotExecutionGuard guard) throws SQLException {
		if (batchSize <= 0) throw new IllegalArgumentException("batchSize must be greater than zero");
		java.util.Objects.requireNonNull(guard, "guard");
		if (!connection.getAutoCommit() && !supportsCallerTransactionAtomicity()) {
			throw new SQLException("The " + dialect.getProductName()
					+ " set-based snapshot staging DDL cannot preserve a caller-owned transaction; "
					+ "use an auto-commit connection so the executor can own the snapshot transaction");
		}
		final List<String> data = new ArrayList<>(definition.keyColumns()); data.addAll(definition.trackedColumns());
		validate(table, definition, data);
		final String target = dialect.getObjectFullName(table.getCatalogName(), table.getSchemaName(), table.getName());
		final String stage = stageIdentifier(stageName());
		try (var scope = BulkUpsertExecutionScope.begin(connection, true)) {
			try {
				MigrationSnapshotTargetValidator.beforeMutation(connection, dialect, target, definition, effectiveAt);
				try (var statement = connection.createStatement()) { statement.execute(createStageSql(stage, target, data)); }
				final String cleanupSql = cleanupStageSql(stage);
				// A DROP may implicitly commit on databases such as HSQLDB. When the
				// caller owns the transaction, retain the connection-local object until
				// that connection ends instead of violating the caller's boundary.
				if (cleanupSql != null && scope.isTransactionManaged()) scope.addCleanupSql(cleanupSql);
				stage(connection, stage, data, definition.keyColumns(), sourceRows, batchSize);
				requireValidKeys(connection, stage, definition.keyColumns(), null, false, "source snapshot");
				final long unchanged = unchanged(connection, target, stage, definition);
				final long expired = close(connection, target, stage, definition, effectiveAt);
				final long inserted = insert(connection, target, stage, definition, data, effectiveAt);
				MigrationSnapshotTargetValidator.afterMutation(connection, dialect, target, definition);
				guard.check();
				scope.commit();
				return new MigrationSnapshotExecutionResult(expired, inserted, unchanged);
			} catch (SQLException | RuntimeException e) { scope.rollback(e); throw e; }
		}
	}

	private void stage(final Connection c, final String stage, final List<String> columns, final List<String> keys,
			final Iterable<? extends Map<String, Object>> rows, final int batchSize) throws SQLException {
		final String sql = "INSERT INTO " + stage + " (" + list(columns, null) + ") VALUES ("
				+ String.join(", ", java.util.Collections.nCopies(columns.size(), "?")) + ")";
		try (var st = c.prepareStatement(sql)) {
			final var keyNames = new HashSet<>(keys);
			int pending = 0;
			for (final Map<String, Object> row : rows) {
				for (int i = 0; i < columns.size(); i++) {
					final String column = columns.get(i);
					if (!row.containsKey(column)) {
						throw new IllegalArgumentException("source snapshot row is missing column: " + column);
					}
					final Object value = row.get(column);
					if (value == null && keyNames.contains(column)) {
						throw new IllegalArgumentException("source snapshot key must not contain null: " + column);
					}
					st.setObject(i + 1, value);
				}
				st.addBatch(); if (++pending == batchSize) { st.executeBatch(); pending = 0; }
			}
			if (pending > 0) st.executeBatch();
		}
	}

	private void requireValidKeys(final Connection c, final String table, final List<String> keys,
			final String condition, final boolean rejectNulls, final String side) throws SQLException {
		final String keyList = list(keys, "t");
		final StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(table).append(" t");
		if (condition != null) sql.append(" WHERE ").append(condition);
		sql.append(" GROUP BY ").append(keyList).append(" HAVING COUNT(*) > 1");
		if (rejectNulls) {
			for (final String key : keys) sql.append(" OR COUNT(").append(q("t", key)).append(") = 0");
		}
		try (var st = c.createStatement()) {
			st.setMaxRows(1);
			try (var rs = st.executeQuery(sql.toString())) {
				if (rs.next()) throw new SQLException(side + " contains duplicate or null snapshot keys");
			}
		}
	}

	private long unchanged(final Connection c, final String target, final String stage,
			final MigrationSnapshotDefinition d) throws SQLException {
		final String equal = d.trackedColumns().stream().map(x -> valuesEqual(q("t", x), q("s", x)))
				.collect(Collectors.joining(" AND "));
		final String sql = "SELECT COUNT(*) FROM " + target + " t JOIN " + stage + " s ON " + match(d.keyColumns())
				+ " WHERE " + q("t", d.validToColumn()) + " IS NULL AND (" + equal + ")";
		try (var st = c.createStatement(); var rs = st.executeQuery(sql)) { rs.next(); return rs.getLong(1); }
	}

	private long close(final Connection c, final String target, final String stage,
			final MigrationSnapshotDefinition d, final Instant at) throws SQLException {
		final String changed = d.trackedColumns().stream().map(x -> valuesDifferent(q("t", x), q("s", x)))
				.collect(Collectors.joining(" OR "));
		final StringBuilder condition = new StringBuilder("EXISTS (SELECT 1 FROM ").append(stage).append(" s WHERE ")
				.append(match(d.keyColumns())).append(" AND (").append(changed).append("))");
		if (d.expireMissingRows()) condition.append(" OR NOT EXISTS (SELECT 1 FROM ").append(stage)
				.append(" s WHERE ").append(match(d.keyColumns())).append(")");
		final StringBuilder sql = new StringBuilder(updateTarget(target)).append(" SET ")
				.append(dialect.quote(d.validToColumn())).append("=?");
		if (d.currentColumn() != null) sql.append(", ").append(dialect.quote(d.currentColumn())).append("=?");
		sql.append(updateFrom(target)).append(" WHERE ").append(q("t", d.validToColumn())).append(" IS NULL AND (")
				.append(condition).append(")");
		try (var st = c.prepareStatement(sql.toString())) {
			st.setTimestamp(1, Timestamp.from(at)); if (d.currentColumn() != null) st.setBoolean(2, false);
			return st.executeUpdate();
		}
	}

	private long insert(final Connection c, final String target, final String stage,
			final MigrationSnapshotDefinition d, final List<String> data, final Instant at) throws SQLException {
		final List<String> targetColumns = new ArrayList<>(data); targetColumns.add(d.validFromColumn());
		targetColumns.add(d.validToColumn()); if (d.currentColumn() != null) targetColumns.add(d.currentColumn());
		final StringBuilder sql = new StringBuilder("INSERT INTO ").append(target).append(" (").append(list(targetColumns, null))
				.append(") SELECT ").append(list(data, "s")).append(", ?, NULL");
		if (d.currentColumn() != null) sql.append(", ?");
		sql.append(" FROM ").append(stage).append(" s WHERE NOT EXISTS (SELECT 1 FROM ").append(target)
				.append(" t WHERE ").append(match(d.keyColumns())).append(" AND ").append(q("t", d.validToColumn())).append(" IS NULL)");
		try (var st = c.prepareStatement(sql.toString())) {
			st.setTimestamp(1, Timestamp.from(at)); if (d.currentColumn() != null) st.setBoolean(2, true);
			return st.executeUpdate();
		}
	}

	protected final String list(final List<String> values, final String alias) {
		return values.stream().map(x -> q(alias, x)).collect(Collectors.joining(", "));
	}
	private String match(final List<String> values) {
		return values.stream().map(x -> q("t", x) + " = " + q("s", x)).collect(Collectors.joining(" AND "));
	}
	private String q(final String alias, final String value) {
		return (alias == null ? "" : alias + ".") + dialect.quote(value);
	}
	private static void validate(final Table table, final MigrationSnapshotDefinition d, final List<String> data) {
		final List<String> all = new ArrayList<>(data); all.add(d.validFromColumn()); all.add(d.validToColumn());
		if (d.currentColumn() != null) all.add(d.currentColumn());
		for (final String name : all) if (table.getColumns().get(name) == null)
			throw new IllegalArgumentException("Snapshot column is not present in target table: " + name);
	}
}
