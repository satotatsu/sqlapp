/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.List;
import java.util.HashSet;
import java.util.Objects;

import lombok.Value;

/** Aggregated verification results for a multi-table migration job. */
@Value
public class BulkMigrationJobVerificationResult {
	List<BulkMigrationJobTaskVerificationResult> tasks;

	public BulkMigrationJobVerificationResult(
			final List<BulkMigrationJobTaskVerificationResult> tasks) {
		Objects.requireNonNull(tasks, "tasks");
		if (tasks.stream().anyMatch(Objects::isNull)) {
			throw new NullPointerException("tasks must not contain null");
		}
		final var taskIds = new HashSet<String>();
		for (final var task : tasks) {
			if (!taskIds.add(task.getTaskId())) {
				throw new IllegalArgumentException("Duplicate verification task ID: "
						+ task.getTaskId());
			}
		}
		this.tasks = List.copyOf(tasks);
	}

	public boolean isMatch() {
		return tasks.stream().map(BulkMigrationJobTaskVerificationResult::getVerificationResult)
				.allMatch(BulkMigrationVerificationResult::isMatch);
	}

	public long getMismatchedTasks() {
		return tasks.stream().map(BulkMigrationJobTaskVerificationResult::getVerificationResult)
				.filter(result -> !result.isMatch()).count();
	}

	public long getExpectedRows() {
		return tasks.stream().map(BulkMigrationJobTaskVerificationResult::getVerificationResult)
				.mapToLong(BulkMigrationVerificationResult::getExpectedRows).sum();
	}

	public long getActualRows() {
		return tasks.stream().map(BulkMigrationJobTaskVerificationResult::getVerificationResult)
				.mapToLong(BulkMigrationVerificationResult::getActualRows).sum();
	}
}
