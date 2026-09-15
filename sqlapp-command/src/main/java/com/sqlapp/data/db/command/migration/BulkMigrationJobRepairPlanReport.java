/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Stable JSON snapshot of a dependency-ordered migration job repair plan. */
public record BulkMigrationJobRepairPlanReport(int formatVersion, Instant generatedAt, String planFingerprint,
		long estimatedReplayRows, long mismatchChunks, boolean atomic, List<Task> tasks) {
	public static final int CURRENT_FORMAT_VERSION = 1;

	public BulkMigrationJobRepairPlanReport {
		tasks = List.copyOf(Objects.requireNonNull(tasks, "tasks"));
	}

	public record Task(String taskId, BulkMigrationRepairPlanReport repairPlan) {
	}
}
