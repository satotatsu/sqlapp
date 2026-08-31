/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.time.Instant;
import java.util.List;

/** Stable, read-only operational snapshot for a bulk migration job. */
public record BulkMigrationOperationalReport(int formatVersion, Instant generatedAt,
		String planFingerprint, boolean compatible, long processedRows,
		long completedTasks, int totalTasks, List<Task> tasks,
		List<Operation> operations, Maintenance maintenance, Progress progress) {
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
}
