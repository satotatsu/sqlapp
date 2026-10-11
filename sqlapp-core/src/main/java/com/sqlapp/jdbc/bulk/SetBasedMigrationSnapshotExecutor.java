/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.MigrationSnapshotDefinition;

/** Database-specific staging and set-based SCD2 executor. */
@FunctionalInterface
public interface SetBasedMigrationSnapshotExecutor {
	MigrationSnapshotExecutionResult execute(Connection connection, Table targetTable,
			MigrationSnapshotDefinition definition, Instant effectiveAt,
			Iterable<? extends Map<String, Object>> sourceRows, int batchSize) throws SQLException;

	default MigrationSnapshotExecutionResult execute(final Connection connection, final Table targetTable,
			final MigrationSnapshotDefinition definition, final Instant effectiveAt,
			final Iterable<? extends Map<String, Object>> sourceRows, final int batchSize,
			final MigrationSnapshotExecutionGuard guard) throws SQLException {
		Objects.requireNonNull(guard, "guard");
		if (guard != MigrationSnapshotExecutionGuard.NO_OP) {
			throw new SQLException("Set-based snapshot executor does not support a commit-time execution guard");
		}
		return execute(connection, targetTable, definition, effectiveAt, sourceRows, batchSize);
	}

	/**
	 * Whether staging DDL preserves a transaction already owned by the caller.
	 */
	default boolean supportsCallerTransactionAtomicity() {
		return true;
	}
}
