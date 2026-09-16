/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.MigrationSnapshotChange;
import com.sqlapp.data.schemas.migration.MigrationSnapshotDefinition;
import com.sqlapp.data.schemas.migration.MigrationSnapshotPlan;
import com.sqlapp.data.schemas.migration.MigrationSnapshotStreamingPlanner;

/** Applies an SCD2 plan with two reused prepared statements and JDBC batches. */
public final class JdbcBatchMigrationSnapshotExecutor {
	private JdbcBatchMigrationSnapshotExecutor() {
	}

	public static MigrationSnapshotExecutionResult execute(final Connection connection, final Table table,
			final MigrationSnapshotPlan plan, final int batchSize) throws SQLException {
		if (connection == null || table == null || plan == null) {
			throw new IllegalArgumentException("Connection, table, and snapshot plan are required");
		}
		if (batchSize <= 0) {
			throw new IllegalArgumentException("batchSize must be greater than zero");
		}
		final MigrationSnapshotDefinition definition = plan.definition();
		validateColumns(table, definition);
		final var dialect = DialectResolver.getInstance().getDialect(connection);
		final String tableName = dialect.getObjectFullName(table.getCatalogName(), table.getSchemaName(), table.getName());
		final List<String> dataColumns = new ArrayList<>(definition.keyColumns());
		dataColumns.addAll(definition.trackedColumns());
		final String closeSql = closeSql(dialect::quote, tableName, definition);
		final String insertSql = insertSql(dialect::quote, tableName, definition, dataColumns);
		try (var scope = BulkUpsertExecutionScope.begin(connection, true)) {
			try {
				final long expired = close(connection, closeSql, definition, plan, batchSize);
				final long inserted = insert(connection, insertSql, definition, dataColumns, plan, batchSize);
				scope.commit();
				return new MigrationSnapshotExecutionResult(expired, inserted, plan.unchangedRows());
			} catch (SQLException | RuntimeException e) {
				scope.rollback(e);
				throw e;
			}
		}
	}

	/**
	 * Compares strictly key-ordered inputs and applies them while retaining at most
	 * {@code batchSize} changes in memory.
	 */
	public static MigrationSnapshotExecutionResult executeStreaming(final Connection connection, final Table table,
			final MigrationSnapshotDefinition definition, final java.time.Instant effectiveAt,
			final Iterable<? extends java.util.Map<String, Object>> sourceRows,
			final Iterable<? extends java.util.Map<String, Object>> currentRows, final int batchSize) throws SQLException {
		if (connection == null || table == null || definition == null || effectiveAt == null) {
			throw new IllegalArgumentException("Connection, table, definition, and effectiveAt are required");
		}
		if (batchSize <= 0) {
			throw new IllegalArgumentException("batchSize must be greater than zero");
		}
		validateColumns(table, definition);
		final var dialect = DialectResolver.getInstance().getDialect(connection);
		final String tableName = dialect.getObjectFullName(table.getCatalogName(), table.getSchemaName(), table.getName());
		final List<String> dataColumns = new ArrayList<>(definition.keyColumns());
		dataColumns.addAll(definition.trackedColumns());
		try (var closeStatement = connection.prepareStatement(closeSql(dialect::quote, tableName, definition));
				var insertStatement = connection.prepareStatement(insertSql(dialect::quote, tableName, definition, dataColumns));
				var scope = BulkUpsertExecutionScope.begin(connection, true)) {
			try {
				final StreamingBatch batch = new StreamingBatch(closeStatement, insertStatement, definition, dataColumns,
						effectiveAt, batchSize);
				final var summary = MigrationSnapshotStreamingPlanner.plan(definition, sourceRows, currentRows, batch::add);
				batch.flush();
				scope.commit();
				return new MigrationSnapshotExecutionResult(summary.updatedRows() + summary.expiredRows(),
						summary.insertedRows() + summary.updatedRows(), summary.unchangedRows());
			} catch (SQLException | RuntimeException e) {
				scope.rollback(e);
				throw e;
			}
		}
	}

	private static final class StreamingBatch {
		private final PreparedStatement closeStatement;
		private final PreparedStatement insertStatement;
		private final MigrationSnapshotDefinition definition;
		private final List<String> dataColumns;
		private final java.time.Instant effectiveAt;
		private final int batchSize;
		private final List<MigrationSnapshotChange> changes = new ArrayList<>();

		private StreamingBatch(final PreparedStatement closeStatement, final PreparedStatement insertStatement,
				final MigrationSnapshotDefinition definition, final List<String> dataColumns,
				final java.time.Instant effectiveAt, final int batchSize) {
			this.closeStatement = closeStatement;
			this.insertStatement = insertStatement;
			this.definition = definition;
			this.dataColumns = dataColumns;
			this.effectiveAt = effectiveAt;
			this.batchSize = batchSize;
		}

		private void add(final MigrationSnapshotChange change) throws SQLException {
			changes.add(change);
			if (changes.size() == batchSize) {
				flush();
			}
		}

		private void flush() throws SQLException {
			if (changes.isEmpty()) {
				return;
			}
			int closeCount = 0;
			for (final MigrationSnapshotChange change : changes) {
				if (change.type() != MigrationSnapshotChange.Type.INSERT) {
					bindClose(closeStatement, definition, effectiveAt, change);
					closeStatement.addBatch();
					closeCount++;
				}
			}
			if (closeCount > 0) {
				requireOnePerChange(closeStatement.executeBatch());
			}
			int insertCount = 0;
			for (final MigrationSnapshotChange change : changes) {
				if (change.type() != MigrationSnapshotChange.Type.EXPIRE) {
					bindInsert(insertStatement, definition, dataColumns, effectiveAt, change);
					insertStatement.addBatch();
					insertCount++;
				}
			}
			if (insertCount > 0) {
				requireOnePerChange(insertStatement.executeBatch());
			}
			changes.clear();
		}
	}

	private static void bindClose(final PreparedStatement statement, final MigrationSnapshotDefinition definition,
			final java.time.Instant effectiveAt, final MigrationSnapshotChange change) throws SQLException {
		int parameter = 1;
		statement.setTimestamp(parameter++, Timestamp.from(effectiveAt));
		if (definition.currentColumn() != null) {
			statement.setBoolean(parameter++, false);
		}
		for (final String key : definition.keyColumns()) {
			statement.setObject(parameter++, change.key().get(key));
		}
		if (definition.currentColumn() != null) {
			statement.setBoolean(parameter, true);
		}
	}

	private static void bindInsert(final PreparedStatement statement, final MigrationSnapshotDefinition definition,
			final List<String> dataColumns, final java.time.Instant effectiveAt,
			final MigrationSnapshotChange change) throws SQLException {
		int parameter = 1;
		for (final String column : dataColumns) {
			statement.setObject(parameter++, change.sourceRow().get(column));
		}
		statement.setTimestamp(parameter++, Timestamp.from(effectiveAt));
		statement.setObject(parameter++, null);
		if (definition.currentColumn() != null) {
			statement.setBoolean(parameter, true);
		}
	}

	private static long close(final Connection connection, final String sql,
			final MigrationSnapshotDefinition definition, final MigrationSnapshotPlan plan, final int batchSize)
			throws SQLException {
		long affected = 0;
		int pending = 0;
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			for (final MigrationSnapshotChange change : plan.changes()) {
				if (change.type() == MigrationSnapshotChange.Type.INSERT) {
					continue;
				}
				int parameter = 1;
				statement.setTimestamp(parameter++, Timestamp.from(plan.effectiveAt()));
				if (definition.currentColumn() != null) {
					statement.setBoolean(parameter++, false);
				}
				for (final String key : definition.keyColumns()) {
					statement.setObject(parameter++, change.key().get(key));
				}
				if (definition.currentColumn() != null) {
					statement.setBoolean(parameter, true);
				}
				statement.addBatch();
				if (++pending == batchSize) {
					affected += requireOnePerChange(statement.executeBatch());
					pending = 0;
				}
			}
			if (pending > 0) {
				affected += requireOnePerChange(statement.executeBatch());
			}
		}
		return affected;
	}

	private static long insert(final Connection connection, final String sql,
			final MigrationSnapshotDefinition definition, final List<String> dataColumns,
			final MigrationSnapshotPlan plan, final int batchSize) throws SQLException {
		long affected = 0;
		int pending = 0;
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			for (final MigrationSnapshotChange change : plan.changes()) {
				if (change.type() == MigrationSnapshotChange.Type.EXPIRE) {
					continue;
				}
				int parameter = 1;
				for (final String column : dataColumns) {
					statement.setObject(parameter++, change.sourceRow().get(column));
				}
				statement.setTimestamp(parameter++, Timestamp.from(plan.effectiveAt()));
				statement.setObject(parameter++, null);
				if (definition.currentColumn() != null) {
					statement.setBoolean(parameter, true);
				}
				statement.addBatch();
				if (++pending == batchSize) {
					affected += requireOnePerChange(statement.executeBatch());
					pending = 0;
				}
			}
			if (pending > 0) {
				affected += requireOnePerChange(statement.executeBatch());
			}
		}
		return affected;
	}

	private static long requireOnePerChange(final int[] counts) throws SQLException {
		long affected = 0;
		for (final int count : counts) {
			if (count == Statement.EXECUTE_FAILED || count == 0 || count > 1) {
				throw new SQLException("SCD2 batch did not affect exactly one current row");
			}
			if (count < 0 && count != Statement.SUCCESS_NO_INFO) {
				throw new SQLException("Invalid SCD2 JDBC batch update count: " + count);
			}
			affected++;
		}
		return affected;
	}

	private static String closeSql(final java.util.function.Function<String, String> quote, final String table,
			final MigrationSnapshotDefinition definition) {
		final StringBuilder sql = new StringBuilder("UPDATE ").append(table).append(" SET ")
				.append(quote.apply(definition.validToColumn())).append(" = ?");
		if (definition.currentColumn() != null) {
			sql.append(", ").append(quote.apply(definition.currentColumn())).append(" = ?");
		}
		sql.append(" WHERE ");
		for (int i = 0; i < definition.keyColumns().size(); i++) {
			if (i > 0) {
				sql.append(" AND ");
			}
			sql.append(quote.apply(definition.keyColumns().get(i))).append(" = ?");
		}
		if (definition.currentColumn() != null) {
			sql.append(" AND ").append(quote.apply(definition.currentColumn())).append(" = ?");
		} else {
			sql.append(" AND ").append(quote.apply(definition.validToColumn())).append(" IS NULL");
		}
		return sql.toString();
	}

	private static String insertSql(final java.util.function.Function<String, String> quote, final String table,
			final MigrationSnapshotDefinition definition, final List<String> dataColumns) {
		final List<String> columns = new ArrayList<>(dataColumns);
		columns.add(definition.validFromColumn());
		columns.add(definition.validToColumn());
		if (definition.currentColumn() != null) {
			columns.add(definition.currentColumn());
		}
		return "INSERT INTO " + table + " (" + columns.stream().map(quote).collect(java.util.stream.Collectors.joining(", "))
				+ ") VALUES (" + String.join(", ", java.util.Collections.nCopies(columns.size(), "?")) + ")";
	}

	private static void validateColumns(final Table table, final MigrationSnapshotDefinition definition) {
		final List<String> columns = new ArrayList<>(definition.keyColumns());
		columns.addAll(definition.trackedColumns());
		columns.add(definition.validFromColumn());
		columns.add(definition.validToColumn());
		if (definition.currentColumn() != null) {
			columns.add(definition.currentColumn());
		}
		for (final String column : columns) {
			if (table.getColumns().get(column) == null) {
				throw new IllegalArgumentException("Snapshot column is not present in target table: " + column);
			}
		}
	}
}
