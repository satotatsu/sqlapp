/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.sqlapp.data.schemas.Table.TableOrder;

/** Repairs verified mismatches for every table in dependency order. */
public final class BulkMigrationJobRepairExecutor {
	private BulkMigrationJobRepairExecutor() {
	}

	public static BulkMigrationJobRepairResult execute(final Connection targetConnection,
			final List<BulkMigrationJobRepairTask> tasks) throws SQLException {
		Objects.requireNonNull(targetConnection, "targetConnection");
		Objects.requireNonNull(tasks, "tasks");
		final Set<String> taskIds = new HashSet<>();
		for (final BulkMigrationJobRepairTask task : tasks) {
			Objects.requireNonNull(task, "task");
			if (task.getTaskId() == null || task.getTaskId().isBlank()) {
				throw new IllegalArgumentException("taskId must not be empty");
			}
			if (!taskIds.add(task.getTaskId())) {
				throw new IllegalArgumentException("Duplicate repair task ID: " + task.getTaskId());
			}
			if ((task.getExpected() == null) == (task.getExpectedKeysetSource() == null)) {
				throw new IllegalArgumentException("Exactly one of expected or "
						+ "expectedKeysetSource is required for task " + task.getTaskId());
			}
			Objects.requireNonNull(task.getExpectedTable(), "expectedTable");
			Objects.requireNonNull(task.getVerificationResult(), "verificationResult");
			Objects.requireNonNull(task.getOptions(), "options");
		}
		final List<BulkMigrationJobRepairTask> ordered = TableOrder.CREATE.sort(
				tasks, BulkMigrationJobRepairTask::getExpectedTable);
		for (final BulkMigrationJobRepairTask task : ordered) {
			if (task.getExpectedKeysetSource() != null) {
				try {
					BulkMigrationRepairExecutor.validateKeysetSource(
							task.getExpectedKeysetSource(), task.getVerificationResult());
				} catch (RuntimeException e) {
					throw new BulkMigrationJobRepairException(task.getTaskId(),
							new BulkMigrationJobRepairResult(List.of()), e);
				}
			}
		}
		final List<BulkMigrationJobTaskRepairResult> results = new ArrayList<>(ordered.size());
		for (final BulkMigrationJobRepairTask task : ordered) {
			try {
				final BulkMigrationRepairResult result = task.getExpectedKeysetSource() == null
						? BulkMigrationRepairExecutor.execute(targetConnection, task.getExpected(),
								task.getVerificationResult(), task.getOptions())
						: BulkMigrationRepairExecutor.execute(targetConnection,
								task.getExpectedKeysetSource(), task.getVerificationResult(),
								task.getOptions());
				results.add(new BulkMigrationJobTaskRepairResult(task.getTaskId(), result));
			} catch (SQLException | RuntimeException e) {
				throw new BulkMigrationJobRepairException(task.getTaskId(),
						new BulkMigrationJobRepairResult(List.copyOf(results)), e);
			}
		}
		return new BulkMigrationJobRepairResult(List.copyOf(results));
	}
}
