/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.Objects;

import lombok.Value;

/** Result of one table task in dependency execution order. */
@Value
public class BulkMigrationJobTaskResult {
	String taskId;
	ChunkedBulkMigrationResult migrationResult;

	public BulkMigrationJobTaskResult(final String taskId,
			final ChunkedBulkMigrationResult migrationResult) {
		if (taskId == null || taskId.isBlank()) {
			throw new IllegalArgumentException("taskId must not be empty");
		}
		this.taskId = taskId;
		this.migrationResult = Objects.requireNonNull(migrationResult, "migrationResult");
	}
}
