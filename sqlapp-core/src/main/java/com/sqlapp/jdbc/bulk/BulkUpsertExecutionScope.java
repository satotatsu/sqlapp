/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Transaction and staging-object cleanup scope shared by bulk-upsert executors.
 * Database-specific cleanup SQL is registered only after its object is created.
 */
public final class BulkUpsertExecutionScope implements AutoCloseable {
	private final Connection connection;
	private final BulkUpsertTransaction transaction;
	private final List<String> cleanupSql = new ArrayList<>();

	public static BulkUpsertExecutionScope begin(final Connection connection,
			final boolean useTransaction) throws SQLException {
		return new BulkUpsertExecutionScope(connection, useTransaction);
	}

	private BulkUpsertExecutionScope(final Connection connection,
			final boolean useTransaction) throws SQLException {
		this.connection = java.util.Objects.requireNonNull(connection, "connection");
		this.transaction = BulkUpsertTransaction.begin(connection, useTransaction);
	}

	public void addCleanupSql(final String sql) {
		cleanupSql.add(java.util.Objects.requireNonNull(sql, "sql"));
	}

	public void commit() throws SQLException {
		transaction.commit();
	}

	public void rollback(final Throwable failure) {
		transaction.rollback(failure);
	}

	@Override
	public void close() throws SQLException {
		SQLException failure = null;
		for (final String sql : cleanupSql) {
			try (var statement = connection.createStatement()) {
				statement.execute(sql);
			} catch (SQLException e) {
				if (failure == null) {
					failure = e;
				} else {
					failure.addSuppressed(e);
				}
			}
		}
		try {
			transaction.close();
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
