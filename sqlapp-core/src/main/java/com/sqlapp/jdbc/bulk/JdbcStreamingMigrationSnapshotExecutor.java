/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.MigrationSnapshotDefinition;

/** Streams ordered JDBC tables directly into the bounded-memory SCD2 executor. */
public final class JdbcStreamingMigrationSnapshotExecutor {
	private JdbcStreamingMigrationSnapshotExecutor() {
	}

	public static MigrationSnapshotExecutionResult execute(final Connection sourceConnection,
			final Connection targetConnection, final Table sourceTable, final Table targetTable,
			final MigrationSnapshotDefinition definition, final Instant effectiveAt, final int fetchSize,
			final int batchSize) throws SQLException {
		if (sourceConnection == null || targetConnection == null || sourceTable == null || targetTable == null) {
			throw new IllegalArgumentException("Source/target connections and tables are required");
		}
		if (fetchSize <= 0) {
			throw new IllegalArgumentException("fetchSize must be greater than zero");
		}
		final List<String> columns = new ArrayList<>(definition.keyColumns());
		columns.addAll(definition.trackedColumns());
		validateColumns(sourceTable, columns, "source");
		validateColumns(targetTable, columns, "target");
		final Dialect sourceDialect = DialectResolver.getInstance().getDialect(sourceConnection);
		final Dialect targetDialect = DialectResolver.getInstance().getDialect(targetConnection);
		try (var source = new RowStream(sourceConnection, selectSql(sourceDialect, sourceTable, definition, columns, false),
				columns, fetchSize);
				var current = new RowStream(targetConnection,
						selectSql(targetDialect, targetTable, definition, columns, true), columns, fetchSize)) {
			try {
				return JdbcBatchMigrationSnapshotExecutor.executeStreaming(targetConnection, targetTable, definition,
						effectiveAt, source, current, batchSize);
			} catch (CursorFailure e) {
				throw e.sqlException;
			}
		}
	}

	private static String selectSql(final Dialect dialect, final Table table,
			final MigrationSnapshotDefinition definition, final List<String> columns, final boolean currentOnly) {
		final StringBuilder sql = new StringBuilder("SELECT ")
				.append(columns.stream().map(dialect::quote).collect(java.util.stream.Collectors.joining(", ")))
				.append(" FROM ").append(dialect.getObjectFullName(table.getCatalogName(), table.getSchemaName(), table.getName()));
		if (currentOnly) {
			sql.append(" WHERE ").append(dialect.quote(definition.validToColumn())).append(" IS NULL");
		}
		sql.append(" ORDER BY ")
				.append(definition.keyColumns().stream().map(dialect::quote)
						.collect(java.util.stream.Collectors.joining(", ")));
		return sql.toString();
	}

	private static void validateColumns(final Table table, final List<String> columns, final String side) {
		for (final String column : columns) {
			if (table.getColumns().get(column) == null) {
				throw new IllegalArgumentException(side + " snapshot column is not present: " + column);
			}
		}
	}

	private static final class RowStream implements Iterable<Map<String, Object>>, AutoCloseable {
		private final List<String> columns;
		private final Statement statement;
		private final ResultSet resultSet;
		private boolean iteratorCreated;

		private RowStream(final Connection connection, final String sql, final List<String> columns, final int fetchSize)
				throws SQLException {
			this.columns = List.copyOf(columns);
			this.statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			try {
				statement.setFetchSize(fetchSize);
				this.resultSet = statement.executeQuery(sql);
			} catch (SQLException | RuntimeException e) {
				try {
					statement.close();
				} catch (SQLException closeFailure) {
					e.addSuppressed(closeFailure);
				}
				throw e;
			}
		}

		@Override
		public Iterator<Map<String, Object>> iterator() {
			if (iteratorCreated) {
				throw new IllegalStateException("JDBC snapshot row stream can only be consumed once");
			}
			iteratorCreated = true;
			return new Iterator<>() {
				private Boolean available;

				@Override
				public boolean hasNext() {
					if (available == null) {
						try {
							available = resultSet.next();
						} catch (SQLException e) {
							throw new CursorFailure(e);
						}
					}
					return available;
				}

				@Override
				public Map<String, Object> next() {
					if (!hasNext()) {
						throw new NoSuchElementException();
					}
					final Map<String, Object> row = new LinkedHashMap<>();
					try {
						for (int i = 0; i < columns.size(); i++) {
							row.put(columns.get(i), resultSet.getObject(i + 1));
						}
					} catch (SQLException e) {
						throw new CursorFailure(e);
					}
					available = null;
					return row;
				}
			};
		}

		@Override
		public void close() throws SQLException {
			SQLException failure = null;
			try {
				resultSet.close();
			} catch (SQLException e) {
				failure = e;
			}
			try {
				statement.close();
			} catch (SQLException e) {
				if (failure == null) {
					failure = e;
				} else {
					failure.addSuppressed(e);
				}
			}
			if (failure != null) {
				throw failure;
			}
		}
	}

	private static final class CursorFailure extends RuntimeException {
		private static final long serialVersionUID = 1L;
		private final SQLException sqlException;

		private CursorFailure(final SQLException sqlException) {
			super(sqlException);
			this.sqlException = sqlException;
		}
	}
}
