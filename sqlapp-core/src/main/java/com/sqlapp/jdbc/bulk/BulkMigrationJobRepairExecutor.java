/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Repairs verified mismatches for every table in dependency order. */
public final class BulkMigrationJobRepairExecutor {
	private BulkMigrationJobRepairExecutor() {
	}

	public static BulkMigrationJobRepairResult execute(final Connection targetConnection,
			final List<BulkMigrationJobRepairTask> tasks) throws SQLException {
		return execute(targetConnection,
				BulkMigrationJobRepairPlanner.plan(targetConnection, tasks));
	}

	public static BulkMigrationJobRepairResult execute(final Connection targetConnection,
			final BulkMigrationJobRepairPlan plan) throws SQLException {
		Objects.requireNonNull(targetConnection, "targetConnection");
		Objects.requireNonNull(plan, "plan");
		preflight(targetConnection, plan);
		final List<BulkMigrationJobTaskRepairResult> results =
				new ArrayList<>(plan.getTasks().size());
		for (final BulkMigrationJobRepairPlan.Task task : plan.getTasks()) {
			try {
				final BulkMigrationRepairResult result = BulkMigrationRepairExecutor.execute(
						targetConnection, task.repairPlan());
				results.add(new BulkMigrationJobTaskRepairResult(task.taskId(), result));
			} catch (SQLException | RuntimeException e) {
				throw new BulkMigrationJobRepairException(task.taskId(),
						new BulkMigrationJobRepairResult(List.copyOf(results)), e);
			}
		}
		return new BulkMigrationJobRepairResult(List.copyOf(results));
	}

	public static BulkMigrationJobRepairResult execute(final Connection targetConnection,
			final BulkMigrationJobRepairPlan plan, final String approvedFingerprint)
			throws SQLException {
		if (approvedFingerprint == null || approvedFingerprint.isBlank()) {
			throw new IllegalArgumentException("approvedFingerprint must not be empty");
		}
		if (!approvedFingerprint.equals(Objects.requireNonNull(plan, "plan").getFingerprint())) {
			throw new IllegalArgumentException(
					"Approved job repair plan fingerprint does not match the current plan");
		}
		return execute(targetConnection, plan);
	}

	private static void preflight(final Connection connection,
			final BulkMigrationJobRepairPlan plan) throws SQLException {
		try {
			plan.validateUnchanged();
		} catch (RuntimeException e) {
			throw new BulkMigrationJobRepairException("<plan>",
					new BulkMigrationJobRepairResult(List.of()), e);
		}
		for (final BulkMigrationJobRepairPlan.Task task : plan.getTasks()) {
			try {
				BulkMigrationRepairPlanner.validateExecutionConnection(connection,
						task.repairPlan());
			} catch (SQLException | RuntimeException e) {
				throw new BulkMigrationJobRepairException(task.taskId(),
						new BulkMigrationJobRepairResult(List.of()), e);
			}
		}
	}
}
