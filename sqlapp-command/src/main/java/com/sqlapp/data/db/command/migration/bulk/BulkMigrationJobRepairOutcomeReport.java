/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.time.Instant;

/** Stable, bounded JSON summary of a verified migration job repair outcome. */
public record BulkMigrationJobRepairOutcomeReport(int formatVersion, Instant generatedAt, String status,
		String migrationPlanFingerprint, String repairPlanFingerprint, String approvedRepairPlanFileFingerprint,
		String repairExecutionReportFingerprint, String repairFailureReportFingerprint,
		String postRepairVerificationReportFingerprint, String failurePhase, String failedTaskId,
		BulkMigrationArtifactProvenance provenance) {
	public static final int CURRENT_FORMAT_VERSION = 1;
}
