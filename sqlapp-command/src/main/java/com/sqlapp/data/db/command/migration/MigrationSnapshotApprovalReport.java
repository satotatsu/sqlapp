/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Reviewable, connection-free approval artifact for one SCD2 snapshot. */
public record MigrationSnapshotApprovalReport(int formatVersion, Instant generatedAt, String configurationFingerprint,
		String snapshotId, String sourceTable, String targetTable, List<String> keyColumns, List<String> trackedColumns,
		boolean expireMissingRows, Instant effectiveAt, int fetchSize, int batchSize, Duration approvalValidFor) {
	public static final int CURRENT_FORMAT_VERSION = 1;

	public MigrationSnapshotApprovalReport {
		keyColumns = keyColumns == null ? null : List.copyOf(keyColumns);
		trackedColumns = trackedColumns == null ? null : List.copyOf(trackedColumns);
	}
}
