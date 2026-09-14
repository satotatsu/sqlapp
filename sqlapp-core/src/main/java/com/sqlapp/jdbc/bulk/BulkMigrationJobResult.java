/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** Completed multi-table job results in dependency execution order. */
@Getter
@EqualsAndHashCode
@ToString
public class BulkMigrationJobResult {
	private final String planFingerprint;
	private final List<BulkMigrationJobTaskResult> tasks;

	public BulkMigrationJobResult(final List<BulkMigrationJobTaskResult> tasks) {
		this(null, tasks);
	}

	public BulkMigrationJobResult(final String planFingerprint,
			final List<BulkMigrationJobTaskResult> tasks) {
		if (planFingerprint != null && planFingerprint.isBlank()) {
			throw new IllegalArgumentException("planFingerprint must not be empty");
		}
		Objects.requireNonNull(tasks, "tasks");
		final Set<String> taskIds = new HashSet<>();
		for (final BulkMigrationJobTaskResult task : tasks) {
			Objects.requireNonNull(task, "task");
			if (!taskIds.add(task.getTaskId())) {
				throw new IllegalArgumentException("Duplicate migration result task ID: "
						+ task.getTaskId());
			}
		}
		this.planFingerprint = planFingerprint;
		this.tasks = List.copyOf(tasks);
	}

	public long getProcessedRows() {
		return tasks.stream().map(BulkMigrationJobTaskResult::getMigrationResult)
				.mapToLong(ChunkedBulkMigrationResult::getProcessedRows).sum();
	}

	public long getAlreadyCompleteTasks() {
		return tasks.stream().map(BulkMigrationJobTaskResult::getMigrationResult)
				.filter(ChunkedBulkMigrationResult::isAlreadyComplete).count();
	}
}
