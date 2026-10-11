/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import com.sqlapp.data.schemas.Table;

import lombok.Builder;
import lombok.Value;

/** Expected and actual row streams for one job task verification. */
@Value
@Builder
public class BulkMigrationJobVerificationTask {
	String taskId;
	Table expected;
	Table actual;
	int chunkSize;

	private BulkMigrationJobVerificationTask(final String taskId, final Table expected,
			final Table actual, final int chunkSize) {
		if (taskId == null || taskId.isBlank()) {
			throw new IllegalArgumentException("taskId must not be empty");
		}
		if (chunkSize <= 0) {
			throw new IllegalArgumentException(
					"chunkSize must be greater than zero: " + taskId);
		}
		this.taskId = taskId;
		this.expected = java.util.Objects.requireNonNull(expected, "expected");
		this.actual = java.util.Objects.requireNonNull(actual, "actual");
		this.chunkSize = chunkSize;
	}
}
