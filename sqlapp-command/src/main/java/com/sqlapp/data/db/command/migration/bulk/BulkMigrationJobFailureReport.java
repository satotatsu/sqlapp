/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Stable summary of a failed or intentionally paused migration invocation. */
public record BulkMigrationJobFailureReport(int formatVersion, Instant failedAt, String status, String jobId,
		String planFingerprint, String stoppedTaskId, String failureType, String failureMessage,
		List<BulkMigrationJobExecutionReport.Task> completedTasks, BulkMigrationArtifactProvenance provenance) {
	public static final int CURRENT_FORMAT_VERSION = 1;
	public BulkMigrationJobFailureReport {
		completedTasks = List.copyOf(Objects.requireNonNull(completedTasks, "completedTasks"));
	}
}
