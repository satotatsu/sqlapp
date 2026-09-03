/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import com.sqlapp.data.schemas.Table.TableOrder;

/** Builds a fully preflighted repair plan in target dependency order. */
public final class BulkMigrationJobRepairPlanner {
	private BulkMigrationJobRepairPlanner() {
	}

	public static BulkMigrationJobRepairPlan plan(final Connection targetConnection,
			final List<BulkMigrationJobRepairTask> tasks) throws SQLException {
		Objects.requireNonNull(targetConnection, "targetConnection");
		Objects.requireNonNull(tasks, "tasks");
		final var ids = new HashSet<String>();
		for (final BulkMigrationJobRepairTask task : tasks) {
			validateTask(task, ids);
		}
		final List<BulkMigrationJobRepairTask> ordered = TableOrder.CREATE.sort(tasks,
				BulkMigrationJobRepairTask::getTargetTable);
		for (final BulkMigrationJobRepairTask task : ordered) {
			try {
				BulkMigrationRepairExecutor.validateConfiguration(task.getExpectedTable(),
						task.getTargetTable(), task.getVerificationResult(), task.getOptions());
				if (task.getExpectedKeysetSource() != null) {
					BulkMigrationRepairExecutor.validateKeysetSource(
							task.getExpectedKeysetSource(), task.getVerificationResult());
				}
			} catch (RuntimeException e) {
				throw new BulkMigrationJobRepairException(task.getTaskId(),
						new BulkMigrationJobRepairResult(List.of()), e);
			}
		}
		final List<BulkMigrationJobRepairPlan.Task> planned = new ArrayList<>(ordered.size());
		for (final BulkMigrationJobRepairTask task : ordered) {
			try {
				final BulkMigrationRepairPlan plan = task.getExpectedKeysetSource() == null
						? BulkMigrationRepairPlanner.plan(targetConnection, task.getExpected(),
								task.getTargetTable(), task.getVerificationResult(), task.getOptions())
						: BulkMigrationRepairPlanner.plan(targetConnection,
								task.getExpectedKeysetSource(), task.getTargetTable(),
								task.getVerificationResult(), task.getOptions());
				planned.add(new BulkMigrationJobRepairPlan.Task(task.getTaskId(), plan));
			} catch (SQLException | RuntimeException e) {
				throw new BulkMigrationJobRepairException(task.getTaskId(),
						new BulkMigrationJobRepairResult(List.of()), e);
			}
		}
		return new BulkMigrationJobRepairPlan(planned);
	}

	private static void validateTask(final BulkMigrationJobRepairTask task,
			final HashSet<String> ids) {
		Objects.requireNonNull(task, "task");
		if (task.getTaskId() == null || task.getTaskId().isBlank()) {
			throw new IllegalArgumentException("taskId must not be empty");
		}
		if (!ids.add(task.getTaskId())) {
			throw new IllegalArgumentException("Duplicate repair task ID: " + task.getTaskId());
		}
		if ((task.getExpected() == null) == (task.getExpectedKeysetSource() == null)) {
			throw new IllegalArgumentException("Exactly one of expected or expectedKeysetSource "
					+ "is required for task " + task.getTaskId());
		}
		Objects.requireNonNull(task.getExpectedTable(), "expectedTable");
		Objects.requireNonNull(task.getTargetTable(), "targetTable");
		Objects.requireNonNull(task.getVerificationResult(), "verificationResult");
		Objects.requireNonNull(task.getOptions(), "options");
	}
}
