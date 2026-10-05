/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Stable JSON evidence for an unsuccessful migration job repair. */
public record BulkMigrationJobRepairFailureReport(int formatVersion, Instant failedAt,
		String migrationPlanFingerprint, String repairPlanFingerprint, String approvedRepairPlanFileFingerprint,
		String postRepairVerificationReportFingerprint, String phase, String failedTaskId, String failureType, String failureMessage,
		List<BulkMigrationJobRepairExecutionReport.Task> completedTasks, BulkMigrationArtifactProvenance provenance) {
	public static final int CURRENT_FORMAT_VERSION = 2;

	public BulkMigrationJobRepairFailureReport {
		completedTasks = List.copyOf(Objects.requireNonNull(completedTasks, "completedTasks"));
	}
}
