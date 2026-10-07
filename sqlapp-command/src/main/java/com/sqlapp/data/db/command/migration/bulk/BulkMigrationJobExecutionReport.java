/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Stable summary of rows committed by one migration-job invocation. */
public record BulkMigrationJobExecutionReport(int formatVersion, Instant completedAt, String jobId,
		String planFingerprint, long processedRows, long alreadyCompleteTasks, List<Task> tasks,
		BulkMigrationArtifactProvenance provenance) {
	public static final int CURRENT_FORMAT_VERSION = 1;

	public BulkMigrationJobExecutionReport {
		tasks = List.copyOf(Objects.requireNonNull(tasks, "tasks"));
	}

	public record Task(String taskId, long previouslyProcessedRows, long processedRows,
			long completedChunks, boolean alreadyComplete) {
	}
}
