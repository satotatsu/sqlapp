/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.List;

import lombok.Value;

/** Read-only checkpoint snapshot for a validated migration job plan. */
@Value
public class BulkMigrationJobStatus {
	String planFingerprint;
	List<BulkMigrationJobTaskStatus> tasks;

	public long getProcessedRows() {
		return tasks.stream().map(BulkMigrationJobTaskStatus::getCheckpoint)
				.filter(java.util.Objects::nonNull)
				.mapToLong(BulkMigrationCheckpoint::getProcessedRows).sum();
	}

	public long getCompletedTasks() {
		return tasks.stream().filter(task -> task.getState() == BulkMigrationJobTaskState.COMPLETE)
				.count();
	}

	public boolean isCompatible() {
		return tasks.stream().noneMatch(task -> task.getState() == BulkMigrationJobTaskState.INCOMPATIBLE);
	}
}
