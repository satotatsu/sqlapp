/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import lombok.Value;

/** Aggregated repair results for a multi-table migration job. */
@Value
public class BulkMigrationJobRepairResult {
	String planFingerprint;
	List<BulkMigrationJobTaskRepairResult> tasks;

	public BulkMigrationJobRepairResult(final List<BulkMigrationJobTaskRepairResult> tasks) {
		this(null, tasks);
	}

	public BulkMigrationJobRepairResult(final String planFingerprint,
			final List<BulkMigrationJobTaskRepairResult> tasks) {
		if (planFingerprint != null && planFingerprint.isBlank()) {
			throw new IllegalArgumentException("planFingerprint must not be empty");
		}
		Objects.requireNonNull(tasks, "tasks");
		final Set<String> taskIds = new HashSet<>();
		for (final BulkMigrationJobTaskRepairResult task : tasks) {
			Objects.requireNonNull(task, "task");
			if (!taskIds.add(task.getTaskId())) {
				throw new IllegalArgumentException("Duplicate repair result task ID: "
						+ task.getTaskId());
			}
		}
		this.planFingerprint = planFingerprint;
		this.tasks = List.copyOf(tasks);
	}

	public long getMismatchChunks() {
		return sum(BulkMigrationRepairResult::getMismatchChunks);
	}

	public long getReplayedChunks() {
		return sum(BulkMigrationRepairResult::getReplayedChunks);
	}

	public long getReplayedRows() {
		return sum(BulkMigrationRepairResult::getReplayedRows);
	}

	public long getAffectedRows() {
		return sum(BulkMigrationRepairResult::getAffectedRows);
	}

	public BulkMigrationJobRepairResult validateAgainst(
			final BulkMigrationJobRepairPlan plan) {
		Objects.requireNonNull(plan, "plan").validateUnchanged();
		if (!plan.getFingerprint().equals(planFingerprint)) {
			throw new IllegalArgumentException(
					"Repair result plan differs from the current repair plan");
		}
		if (!tasks.stream().map(BulkMigrationJobTaskRepairResult::getTaskId).toList()
				.equals(plan.getTaskIds())) {
			throw new IllegalArgumentException(
					"Repair result tasks do not match the repair plan and dependency order");
		}
		return this;
	}

	private long sum(final java.util.function.ToLongFunction<BulkMigrationRepairResult> value) {
		long total = 0;
		for (final BulkMigrationJobTaskRepairResult task : tasks) {
			total = Math.addExact(total, value.applyAsLong(task.getRepairResult()));
		}
		return total;
	}

	public long getTasksRequiringManualReconciliation() {
		return tasks.stream().map(BulkMigrationJobTaskRepairResult::getRepairResult)
				.filter(BulkMigrationRepairResult::requiresManualReconciliation).count();
	}

	public boolean requiresManualReconciliation() {
		return getTasksRequiringManualReconciliation() > 0;
	}
}
