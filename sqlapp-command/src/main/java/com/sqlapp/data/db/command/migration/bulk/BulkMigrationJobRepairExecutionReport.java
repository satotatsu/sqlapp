/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Stable JSON evidence describing an executed migration job repair. */
public record BulkMigrationJobRepairExecutionReport(int formatVersion, Instant completedAt,
		String migrationPlanFingerprint, String repairPlanFingerprint, String approvedRepairPlanFileFingerprint,
		long mismatchChunks, long replayedChunks, long replayedRows, long affectedRows,
		long tasksRequiringManualReconciliation, List<Task> tasks, BulkMigrationArtifactProvenance provenance) {
	public static final int CURRENT_FORMAT_VERSION = 1;

	public BulkMigrationJobRepairExecutionReport {
		tasks = List.copyOf(Objects.requireNonNull(tasks, "tasks"));
	}

	public record Task(String taskId, int mismatchChunks, int replayedChunks, long replayedRows, long affectedRows,
			List<Long> chunksWithExtraActualRows, List<Long> chunksWithoutExpectedRows) {
		public Task {
			chunksWithExtraActualRows = List
					.copyOf(Objects.requireNonNull(chunksWithExtraActualRows, "chunksWithExtraActualRows"));
			chunksWithoutExpectedRows = List
					.copyOf(Objects.requireNonNull(chunksWithoutExpectedRows, "chunksWithoutExpectedRows"));
		}
	}
}
