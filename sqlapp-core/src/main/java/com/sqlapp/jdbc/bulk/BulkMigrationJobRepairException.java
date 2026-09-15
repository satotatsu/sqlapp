/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.SQLException;
import java.util.Objects;

import lombok.Getter;

/** SQL or source-consistency failure of one task in a multi-table repair job. */
@Getter
public class BulkMigrationJobRepairException extends SQLException {
	private static final long serialVersionUID = 1L;

	private final String failedTaskId;
	private final BulkMigrationJobRepairResult completedResult;

	/** Retained source/binary-compatible constructor for SQL failures. */
	public BulkMigrationJobRepairException(final String failedTaskId,
			final BulkMigrationJobRepairResult completedResult, final SQLException cause) {
		this(failedTaskId, completedResult, (Throwable) cause);
	}

	/** Creates a task failure for SQL or source-consistency errors. */
	public BulkMigrationJobRepairException(final String failedTaskId,
			final BulkMigrationJobRepairResult completedResult, final Throwable cause) {
		super("Migration job repair task failed: " + failedTaskId, cause);
		if (failedTaskId == null || failedTaskId.isBlank()) {
			throw new IllegalArgumentException("failedTaskId must not be empty");
		}
		this.failedTaskId = failedTaskId;
		this.completedResult = Objects.requireNonNull(completedResult, "completedResult");
		Objects.requireNonNull(cause, "cause");
	}

	public BulkMigrationJobRepairException(final BulkMigrationJobRepairPlan plan,
			final String failedTaskId,
			final BulkMigrationJobRepairResult completedResult, final Throwable cause) {
		super("Migration job repair task failed: " + failedTaskId, cause);
		if (failedTaskId == null || failedTaskId.isBlank()) {
			throw new IllegalArgumentException("failedTaskId must not be empty");
		}
		this.failedTaskId = failedTaskId;
		this.completedResult = Objects.requireNonNull(completedResult, "completedResult")
				.validateCompletedPrefixAgainst(
						Objects.requireNonNull(plan, "plan"), failedTaskId);
		Objects.requireNonNull(cause, "cause");
	}
}
