/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.sqlapp.data.schemas.Table.TableOrder;

/** Verifies every table in a migration job in dependency order. */
public final class BulkMigrationJobVerifier {
	private BulkMigrationJobVerifier() {
	}

	public static BulkMigrationJobVerificationResult verify(
			final List<BulkMigrationJobVerificationTask> tasks) {
		Objects.requireNonNull(tasks, "tasks");
		final Set<String> taskIds = new HashSet<>();
		for (final BulkMigrationJobVerificationTask task : tasks) {
			Objects.requireNonNull(task, "task");
			if (task.getTaskId() == null || task.getTaskId().isBlank()) {
				throw new IllegalArgumentException("taskId must not be empty");
			}
			if (!taskIds.add(task.getTaskId())) {
				throw new IllegalArgumentException("Duplicate verification task ID: "
						+ task.getTaskId());
			}
			Objects.requireNonNull(task.getExpected(), "expected");
			Objects.requireNonNull(task.getActual(), "actual");
			if (task.getChunkSize() <= 0) {
				throw new IllegalArgumentException("chunkSize must be greater than zero: "
						+ task.getTaskId());
			}
		}
		final List<BulkMigrationJobVerificationTask> ordered = TableOrder.CREATE.sort(
				tasks, BulkMigrationJobVerificationTask::getExpected);
		final List<BulkMigrationJobTaskVerificationResult> results = new ArrayList<>(ordered.size());
		for (final BulkMigrationJobVerificationTask task : ordered) {
			results.add(new BulkMigrationJobTaskVerificationResult(task.getTaskId(),
					task.getExpected().getColumns().stream()
							.map(com.sqlapp.data.schemas.Column::getName).toList(),
					BulkMigrationVerifier.verify(task.getExpected(), task.getActual(),
							task.getChunkSize())));
		}
		return new BulkMigrationJobVerificationResult(List.copyOf(results));
	}
}
