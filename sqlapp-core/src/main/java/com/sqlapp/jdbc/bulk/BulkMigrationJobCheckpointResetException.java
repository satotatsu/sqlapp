/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.SQLException;
import java.util.Objects;

import lombok.Getter;

/** Failure while deleting one task checkpoint from a migration job. */
@Getter
public class BulkMigrationJobCheckpointResetException extends SQLException {
	private static final long serialVersionUID = 1L;

	private final String failedTaskId;
	private final BulkMigrationJobCheckpointResetResult completedResult;

	public BulkMigrationJobCheckpointResetException(final BulkMigrationJobPlan plan,
			final String failedTaskId,
			final BulkMigrationJobCheckpointResetResult completedResult,
			final SQLException cause) {
		super("Migration job checkpoint reset failed: " + failedTaskId, cause);
		if (failedTaskId == null || failedTaskId.isBlank()) {
			throw new IllegalArgumentException("failedTaskId must not be empty");
		}
		this.failedTaskId = failedTaskId;
		final BulkMigrationJobPlan validatedPlan = Objects.requireNonNull(plan, "plan");
		this.completedResult = Objects.requireNonNull(completedResult, "completedResult")
				.validateAgainst(validatedPlan);
		final int failedIndex = validatedPlan.getTaskIds().indexOf(failedTaskId);
		if (failedIndex < 0 || completedResult.getResetTaskIds().size() != failedIndex) {
			throw new IllegalArgumentException(
					"Completed checkpoint resets must precede the failed task");
		}
		Objects.requireNonNull(cause, "cause");
	}
}
