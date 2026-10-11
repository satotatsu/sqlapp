/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.SQLException;
import java.util.Objects;

import lombok.Getter;

/** SQL or source-consistency failure of one task in a multi-table repair job. */
@Getter
public class BulkMigrationJobRepairException extends SQLException {
	private static final long serialVersionUID = 1L;
	public enum Phase { PLANNING, PREFLIGHT, EXECUTION }

	private final String failedTaskId;
	private final BulkMigrationJobRepairResult completedResult;
	private final Phase phase;

	/** Creates a task failure for SQL or source-consistency errors. */
	public BulkMigrationJobRepairException(final String failedTaskId,
			final BulkMigrationJobRepairResult completedResult, final Throwable cause) {
		super("Migration job repair task failed: " + failedTaskId, cause);
		if (failedTaskId == null || failedTaskId.isBlank()) {
			throw new IllegalArgumentException("failedTaskId must not be empty");
		}
		this.failedTaskId = failedTaskId;
		this.completedResult = Objects.requireNonNull(completedResult, "completedResult");
		this.phase = Phase.PLANNING;
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
		this.phase = Phase.EXECUTION;
		Objects.requireNonNull(cause, "cause");
	}

	public static BulkMigrationJobRepairException preflight(
			final BulkMigrationJobRepairPlan plan, final String failedTaskId,
			final BulkMigrationJobRepairResult completedResult, final Throwable cause) {
		return new BulkMigrationJobRepairException(plan, failedTaskId,
				completedResult, cause, Phase.PREFLIGHT);
	}

	private BulkMigrationJobRepairException(final BulkMigrationJobRepairPlan plan,
			final String failedTaskId,
			final BulkMigrationJobRepairResult completedResult, final Throwable cause,
			final Phase phase) {
		super("Migration job repair task failed: " + failedTaskId, cause);
		if (failedTaskId == null || failedTaskId.isBlank()) {
			throw new IllegalArgumentException("failedTaskId must not be empty");
		}
		final BulkMigrationJobRepairPlan validatedPlan = Objects.requireNonNull(plan,
				"plan");
		validatedPlan.validateUnchanged();
		if (!validatedPlan.getTaskIds().contains(failedTaskId)) {
			throw new IllegalArgumentException(
					"Failed task does not belong to the repair plan: " + failedTaskId);
		}
		this.failedTaskId = failedTaskId;
		this.completedResult = Objects.requireNonNull(completedResult, "completedResult");
		if (!validatedPlan.getFingerprint().equals(completedResult.getPlanFingerprint())
				|| !completedResult.getTasks().isEmpty()) {
			throw new IllegalArgumentException(
					"Repair preflight failure requires an empty result for the current plan");
		}
		this.phase = phase;
		Objects.requireNonNull(cause, "cause");
	}
}
