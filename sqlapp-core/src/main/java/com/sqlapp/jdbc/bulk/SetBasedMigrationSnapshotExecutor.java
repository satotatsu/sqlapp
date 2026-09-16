/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;

import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.MigrationSnapshotDefinition;

/** Database-specific staging and set-based SCD2 executor. */
@FunctionalInterface
public interface SetBasedMigrationSnapshotExecutor {
	MigrationSnapshotExecutionResult execute(Connection connection, Table targetTable,
			MigrationSnapshotDefinition definition, Instant effectiveAt,
			Iterable<? extends Map<String, Object>> sourceRows, int batchSize) throws SQLException;

	/**
	 * Whether staging DDL preserves a transaction already owned by the caller.
	 */
	default boolean supportsCallerTransactionAtomicity() {
		return true;
	}
}
