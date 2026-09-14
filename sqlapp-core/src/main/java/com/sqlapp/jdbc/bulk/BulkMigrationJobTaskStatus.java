/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.Objects;

import lombok.Value;

/** Checkpoint-derived status for one planned migration task. */
@Value
public class BulkMigrationJobTaskStatus {
	String taskId;
	BulkMigrationJobTaskState state;
	BulkMigrationCheckpoint checkpoint;

	public BulkMigrationJobTaskStatus(final String taskId,
			final BulkMigrationJobTaskState state,
			final BulkMigrationCheckpoint checkpoint) {
		if (taskId == null || taskId.isBlank()) {
			throw new IllegalArgumentException("taskId must not be empty");
		}
		this.taskId = taskId;
		this.state = Objects.requireNonNull(state, "state");
		this.checkpoint = checkpoint;
	}
}
