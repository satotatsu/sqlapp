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
		return tasks.stream().map(BulkMigrationJobTaskRepairResult::getRepairResult)
				.mapToLong(BulkMigrationRepairResult::getMismatchChunks).sum();
	}

	public long getReplayedChunks() {
		return tasks.stream().map(BulkMigrationJobTaskRepairResult::getRepairResult)
				.mapToLong(BulkMigrationRepairResult::getReplayedChunks).sum();
	}

	public long getReplayedRows() {
		return tasks.stream().map(BulkMigrationJobTaskRepairResult::getRepairResult)
				.mapToLong(BulkMigrationRepairResult::getReplayedRows).sum();
	}

	public long getAffectedRows() {
		return tasks.stream().map(BulkMigrationJobTaskRepairResult::getRepairResult)
				.mapToLong(BulkMigrationRepairResult::getAffectedRows).sum();
	}

	public long getTasksRequiringManualReconciliation() {
		return tasks.stream().map(BulkMigrationJobTaskRepairResult::getRepairResult)
				.filter(BulkMigrationRepairResult::requiresManualReconciliation).count();
	}

	public boolean requiresManualReconciliation() {
		return getTasksRequiringManualReconciliation() > 0;
	}
}
