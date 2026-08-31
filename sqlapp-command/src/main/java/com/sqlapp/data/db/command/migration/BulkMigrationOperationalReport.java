/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Stable, read-only operational snapshot for a bulk migration job. */
public record BulkMigrationOperationalReport(int formatVersion, Instant generatedAt,
		String planFingerprint, boolean compatible, long processedRows,
		long completedTasks, int totalTasks, List<Task> tasks,
		List<Operation> operations, Maintenance maintenance, Progress progress,
		List<Progress> progressByMigration, Execution execution) {
	public static final int CURRENT_FORMAT_VERSION = 1;

	public record Task(String taskId, String migrationId, String catalogName,
			String schemaName, String tableName, String mode, int chunkSize,
			String checkpointMode, String state, Checkpoint checkpoint) {
	}

	public record Checkpoint(String migrationId, String sourceFingerprint,
			String targetFingerprint, long processedRows,
			long completedChunks, int chunkSize, boolean complete,
			String lastChunkHash, String resumeToken) {
	}

	public record Operation(String id, String phase, String description,
			boolean transactionBreaking) {
	}

	public record Maintenance(String status, Instant updatedAt,
			String failureMessage) {
	}

	public record Progress(String migrationId, long processedRows, Long totalRows,
			long elapsedMillis, double rowsPerSecond, Double completionRatio,
			Long estimatedRemainingMillis) {
	}

	public record Execution(String event, String taskId, Instant occurredAt,
			Long processedRows, String failureType, String failureMessage) {
		private static final Set<String> EVENTS = Set.of("JOB_STARTED", "JOB_COMPLETED",
				"JOB_FAILED", "JOB_PAUSED", "TASK_STARTED", "TASK_COMPLETED",
				"TASK_FAILED", "TASK_PAUSED");
		public static final int FAILURE_MESSAGE_MAX_LENGTH = 1_000;

		public Execution {
			if (!EVENTS.contains(event)) {
				throw new IllegalArgumentException("Unknown migration execution event: " + event);
			}
			final boolean taskRequired = event.startsWith("TASK_")
					|| "JOB_PAUSED".equals(event);
			if (taskRequired && (taskId == null || taskId.isBlank())) {
				throw new IllegalArgumentException("execution taskId must not be empty");
			}
			if (!taskRequired && !"JOB_FAILED".equals(event) && taskId != null) {
				throw new IllegalArgumentException("execution taskId is not valid for " + event);
			}
			java.util.Objects.requireNonNull(occurredAt, "occurredAt");
			if (processedRows != null && processedRows < 0) {
				throw new IllegalArgumentException("execution processedRows must not be negative");
			}
			final boolean failed = "TASK_FAILED".equals(event) || "JOB_FAILED".equals(event);
			if (failed != (failureType != null)) {
				throw new IllegalArgumentException(
						"failureType is required only for TASK_FAILED");
			}
			if (!failed && failureMessage != null) {
				throw new IllegalArgumentException(
						"failureMessage is valid only for TASK_FAILED");
			}
			if (failureMessage != null
					&& failureMessage.length() > FAILURE_MESSAGE_MAX_LENGTH) {
				throw new IllegalArgumentException("failureMessage is too long");
			}
		}
	}
}
