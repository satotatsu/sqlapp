/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

/** Affected-row summary for one atomic SCD2 application. */
public record MigrationSnapshotExecutionResult(long expiredRows, long insertedRows, long unchangedRows) {
	public MigrationSnapshotExecutionResult {
		if (expiredRows < 0 || insertedRows < 0 || unchangedRows < 0) {
			throw new IllegalArgumentException("Snapshot execution counts must not be negative");
		}
	}
}
