/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.List;
import java.util.HashSet;
import java.util.Objects;

import lombok.Value;

/** Aggregated verification results for a multi-table migration job. */
@Value
public class BulkMigrationJobVerificationResult {
	String planFingerprint;
	List<BulkMigrationJobTaskVerificationResult> tasks;

	public BulkMigrationJobVerificationResult(
			final List<BulkMigrationJobTaskVerificationResult> tasks) {
		this(null, tasks);
	}

	public BulkMigrationJobVerificationResult(final String planFingerprint,
			final List<BulkMigrationJobTaskVerificationResult> tasks) {
		if (planFingerprint != null && planFingerprint.isBlank()) {
			throw new IllegalArgumentException("planFingerprint must not be empty");
		}
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
		this.planFingerprint = planFingerprint;
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
		return sum(BulkMigrationVerificationResult::getExpectedRows);
	}

	public long getActualRows() {
		return sum(BulkMigrationVerificationResult::getActualRows);
	}

	private long sum(final java.util.function.ToLongFunction<BulkMigrationVerificationResult> value) {
		long total = 0;
		for (final BulkMigrationJobTaskVerificationResult task : tasks) {
			total = Math.addExact(total, value.applyAsLong(task.getVerificationResult()));
		}
		return total;
	}
}
