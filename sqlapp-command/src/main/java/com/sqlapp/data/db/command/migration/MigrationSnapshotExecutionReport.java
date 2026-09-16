/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.time.Instant;
import java.util.List;

/** Stable, reviewable result of one completed atomic SCD2 snapshot. */
public record MigrationSnapshotExecutionReport(int formatVersion, Instant generatedAt, String snapshotId,
		String configurationFingerprint, Instant approvalGeneratedAt, String approvalArtifactFingerprint,
		String sourceTable, String targetTable, List<String> keyColumns, List<String> trackedColumns,
		boolean expireMissingRows, Instant effectiveAt, int fetchSize, int batchSize, String databaseProductName,
		String databaseProductVersion, String executorClassName, boolean callerTransactionAtomicity,
		long expiredRows, long insertedRows, long unchangedRows) {
	public static final int CURRENT_FORMAT_VERSION = 1;

	public MigrationSnapshotExecutionReport {
		keyColumns = keyColumns == null ? null : List.copyOf(keyColumns);
		trackedColumns = trackedColumns == null ? null : List.copyOf(trackedColumns);
	}
}
