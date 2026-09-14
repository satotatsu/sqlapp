/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.Objects;

import lombok.Value;

/** Repair result for one task in a multi-table migration job. */
@Value
public class BulkMigrationJobTaskRepairResult {
	String taskId;
	BulkMigrationRepairResult repairResult;

	public BulkMigrationJobTaskRepairResult(final String taskId,
			final BulkMigrationRepairResult repairResult) {
		if (taskId == null || taskId.isBlank()) {
			throw new IllegalArgumentException("taskId must not be empty");
		}
		this.taskId = taskId;
		this.repairResult = Objects.requireNonNull(repairResult, "repairResult");
	}
}
