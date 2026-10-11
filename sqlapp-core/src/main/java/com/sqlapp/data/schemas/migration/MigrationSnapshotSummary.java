/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.migration;

/** Counts produced by a streaming SCD2 comparison. */
public record MigrationSnapshotSummary(long insertedRows, long updatedRows, long expiredRows, long unchangedRows) {
	public MigrationSnapshotSummary {
		if (insertedRows < 0 || updatedRows < 0 || expiredRows < 0 || unchangedRows < 0) {
			throw new IllegalArgumentException("Snapshot summary counts must not be negative");
		}
	}

	public long changedRows() {
		return insertedRows + updatedRows + expiredRows;
	}
}
