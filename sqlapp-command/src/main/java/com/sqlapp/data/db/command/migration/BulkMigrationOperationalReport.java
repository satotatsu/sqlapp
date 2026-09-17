/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.sqlapp.jdbc.bulk.BulkMigrationCheckpointMode;
import com.sqlapp.jdbc.bulk.BulkMigrationJobOperationPhase;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTaskState;
import com.sqlapp.jdbc.bulk.BulkMigrationMaintenanceStatus;
import com.sqlapp.jdbc.bulk.BulkMigrationMode;

/** Stable, read-only operational snapshot for a bulk migration job. */
public record BulkMigrationOperationalReport(int formatVersion, Instant generatedAt, String jobId,
		String planFingerprint, boolean compatible, long processedRows, long completedTasks, int totalTasks,
		List<Task> tasks, List<Operation> operations, Maintenance maintenance, Progress progress,
		List<Progress> progressByMigration, Execution execution) {
	public static final int CURRENT_FORMAT_VERSION = 2;

	public BulkMigrationOperationalReport {
		tasks = List.copyOf(Objects.requireNonNull(tasks, "tasks"));
		operations = List.copyOf(Objects.requireNonNull(operations, "operations"));
		progressByMigration = List.copyOf(Objects.requireNonNull(progressByMigration, "progressByMigration"));
	}

	public record Task(String taskId, String migrationId, String catalogName, String schemaName, String tableName,
			BulkMigrationMode mode, int chunkSize, BulkMigrationCheckpointMode checkpointMode,
			BulkMigrationJobTaskState state, Checkpoint checkpoint) {
	}

	public record Checkpoint(String migrationId, String sourceFingerprint, String targetFingerprint, long processedRows,
			long completedChunks, int chunkSize, boolean complete, String lastChunkHash, String resumeToken) {
	}

	public record Operation(String id, BulkMigrationJobOperationPhase phase, String description,
			boolean transactionBreaking) {
	}

	public record Maintenance(String planFingerprint, BulkMigrationMaintenanceStatus status, Instant updatedAt,
			String failureMessage) {
	}

	public record Progress(String migrationId, long processedRows, Long totalRows, long elapsedMillis,
			double rowsPerSecond, Double completionRatio, Long estimatedRemainingMillis) {
	}

	public enum ExecutionEvent {
		JOB_STARTED(false, false, false, true),
		JOB_COMPLETED(false, true, false, false),
		JOB_REJECTED(false, false, true, false),
		JOB_FAILED(false, false, true, false),
		JOB_PAUSED(true, true, false, false),
		TASK_STARTED(true, false, false, true),
		TASK_COMPLETED(true, true, false, true),
		TASK_FAILED(true, false, true, false),
		TASK_PAUSED(true, true, false, false);

		private final boolean taskRequired;
		private final boolean processedRowsRequired;
		private final boolean failed;
		private final boolean possiblyRunning;

		ExecutionEvent(final boolean taskRequired, final boolean processedRowsRequired, final boolean failed,
				final boolean possiblyRunning) {
			this.taskRequired = taskRequired;
			this.processedRowsRequired = processedRowsRequired;
			this.failed = failed;
			this.possiblyRunning = possiblyRunning;
		}

		public boolean requiresTask() {
			return taskRequired;
		}

		public boolean requiresProcessedRows() {
			return processedRowsRequired;
		}

		public boolean isFailure() {
			return failed;
		}

		public boolean indicatesPossibleRunning() {
			return possiblyRunning;
		}
	}

	public record Execution(ExecutionEvent event, String taskId, Instant occurredAt, Long processedRows,
			String leaseAcquisitionId, String failureType, String failureMessage) {
		public static final int FAILURE_MESSAGE_MAX_LENGTH = 1_000;

		public Execution {
			if (event == null) {
				throw new IllegalArgumentException("execution event must not be null");
			}
			if (event.requiresTask() && (taskId == null || taskId.isBlank())) {
				throw new IllegalArgumentException("execution taskId must not be empty");
			}
			if (!event.requiresTask() && event != ExecutionEvent.JOB_FAILED && taskId != null) {
				throw new IllegalArgumentException("execution taskId is not valid for " + event);
			}
			java.util.Objects.requireNonNull(occurredAt, "occurredAt");
			if (processedRows != null && processedRows < 0) {
				throw new IllegalArgumentException("execution processedRows must not be negative");
			}
			if (processedRows != null && !event.requiresProcessedRows()) {
				throw new IllegalArgumentException("execution processedRows is not valid for " + event);
			}
			if (event.requiresProcessedRows() && processedRows == null) {
				throw new IllegalArgumentException("execution processedRows is required for " + event);
			}
			if (leaseAcquisitionId != null && (leaseAcquisitionId.isBlank()
					|| leaseAcquisitionId.length() > com.sqlapp.jdbc.bulk.BulkMigrationJobLease.ID_MAX_LENGTH)) {
				throw new IllegalArgumentException("execution leaseAcquisitionId is invalid");
			}
			if (event == ExecutionEvent.JOB_REJECTED && leaseAcquisitionId != null) {
				throw new IllegalArgumentException("execution leaseAcquisitionId is not valid for JOB_REJECTED");
			}
			if (event.isFailure() != (failureType != null)) {
				throw new IllegalArgumentException("failureType is required only for failed execution events");
			}
			if (failureType != null && failureType.isBlank()) {
				throw new IllegalArgumentException("failureType must not be blank");
			}
			if (!event.isFailure() && failureMessage != null) {
				throw new IllegalArgumentException("failureMessage is valid only for failed execution events");
			}
			if (failureMessage != null && failureMessage.isBlank()) {
				throw new IllegalArgumentException("failureMessage must not be blank");
			}
			if (failureMessage != null && failureMessage.length() > FAILURE_MESSAGE_MAX_LENGTH) {
				throw new IllegalArgumentException("failureMessage is too long");
			}
		}
	}
}
