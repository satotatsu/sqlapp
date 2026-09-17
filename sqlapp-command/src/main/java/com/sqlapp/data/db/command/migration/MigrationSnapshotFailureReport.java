/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.time.Instant;

/**
 * Audit artifact for an SCD2 execution that did not produce a success report.
 */
public record MigrationSnapshotFailureReport(int formatVersion, Instant generatedAt, Instant startedAt,
		MigrationSnapshotFailurePhase phase, String snapshotId, String configurationFingerprint,
		Instant approvalGeneratedAt, String approvalArtifactFingerprint, String sourceTable, String targetTable,
		Instant effectiveAt, MigrationSnapshotLeaseEvidence lease, String failureType, String failureMessage) {
	public static final int CURRENT_FORMAT_VERSION = 1;
}
