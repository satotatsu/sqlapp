/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import lombok.Value;

/** Read-only checkpoint snapshot for a validated migration job plan. */
@Value
public class BulkMigrationJobStatus {
	String planFingerprint;
	List<BulkMigrationJobTaskStatus> tasks;

	public BulkMigrationJobStatus(final String planFingerprint,
			final List<BulkMigrationJobTaskStatus> tasks) {
		if (planFingerprint == null || planFingerprint.isBlank()) {
			throw new IllegalArgumentException("planFingerprint must not be empty");
		}
		Objects.requireNonNull(tasks, "tasks");
		final Set<String> taskIds = new HashSet<>();
		for (final BulkMigrationJobTaskStatus task : tasks) {
			Objects.requireNonNull(task, "task");
			if (!taskIds.add(task.getTaskId())) {
				throw new IllegalArgumentException("Duplicate status task ID: "
						+ task.getTaskId());
			}
		}
		this.planFingerprint = planFingerprint;
		this.tasks = List.copyOf(tasks);
	}

	public long getProcessedRows() {
		long rows = 0;
		for (final BulkMigrationJobTaskStatus task : tasks) {
			if (task.getCheckpoint() != null) {
				rows = Math.addExact(rows, task.getCheckpoint().getProcessedRows());
			}
		}
		return rows;
	}

	public BulkMigrationJobStatus validateAgainst(final BulkMigrationJobPlan plan) {
		Objects.requireNonNull(plan, "plan").validateUnchanged();
		if (!plan.getFingerprint().equals(planFingerprint)) {
			throw new IllegalArgumentException("Status fingerprint does not match the migration plan");
		}
		if (!tasks.stream().map(BulkMigrationJobTaskStatus::getTaskId).toList()
				.equals(plan.getTaskIds())) {
			throw new IllegalArgumentException(
					"Status tasks do not match the migration plan execution order");
		}
		for (int i = 0; i < tasks.size(); i++) {
			final BulkMigrationCheckpoint checkpoint = tasks.get(i).getCheckpoint();
			if (checkpoint != null && !plan.getTasks().get(i).getOptions().getMigrationId()
					.equals(checkpoint.getMigrationId())) {
				throw new IllegalArgumentException(
						"Checkpoint migrationId does not match planned task: "
								+ plan.getTaskIds().get(i));
			}
		}
		return this;
	}

	public long getCompletedTasks() {
		return tasks.stream().filter(task -> task.getState() == BulkMigrationJobTaskState.COMPLETE)
				.count();
	}

	public boolean isCompatible() {
		return tasks.stream().noneMatch(task -> task.getState() == BulkMigrationJobTaskState.INCOMPATIBLE);
	}
}
