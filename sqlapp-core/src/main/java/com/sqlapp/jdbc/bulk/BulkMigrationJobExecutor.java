/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Table.TableOrder;

/** Executes table migrations in validated dependency order. */
public final class BulkMigrationJobExecutor {
	private BulkMigrationJobExecutor() {
	}

	public static BulkMigrationJobResult execute(final Connection targetConnection,
			final List<BulkMigrationJobTask> tasks) throws SQLException {
		Objects.requireNonNull(targetConnection, "targetConnection");
		final List<BulkMigrationJobTask> ordered = order(tasks);
		final List<BulkMigrationJobTaskResult> results = new ArrayList<>(ordered.size());
		for (final BulkMigrationJobTask task : ordered) {
			try {
				final ChunkedBulkMigrationResult result;
				if (task.getKeysetSource() != null) {
					result = task.getCheckpointStore() == null
							? ChunkedBulkMigrationExecutor.execute(targetConnection,
									task.getKeysetSource(), task.getOptions())
							: ChunkedBulkMigrationExecutor.execute(targetConnection,
									task.getKeysetSource(), task.getOptions(), task.getCheckpointStore());
				} else {
					result = task.getCheckpointStore() == null
							? ChunkedBulkMigrationExecutor.execute(targetConnection,
									task.getSourceTable(), task.getOptions())
							: ChunkedBulkMigrationExecutor.execute(targetConnection,
									task.getSourceTable(), task.getOptions(), task.getCheckpointStore());
				}
				results.add(new BulkMigrationJobTaskResult(task.getTaskId(), result));
			} catch (SQLException e) {
				throw new BulkMigrationJobException(task.getTaskId(),
						new BulkMigrationJobResult(List.copyOf(results)), e);
			}
		}
		return new BulkMigrationJobResult(List.copyOf(results));
	}

	static List<BulkMigrationJobTask> order(final List<BulkMigrationJobTask> tasks) {
		Objects.requireNonNull(tasks, "tasks");
		final Set<String> taskIds = new HashSet<>();
		final Set<String> migrationIds = new HashSet<>();
		for (final BulkMigrationJobTask task : tasks) {
			Objects.requireNonNull(task, "task");
			if (task.getTaskId() == null || task.getTaskId().isBlank()) {
				throw new IllegalArgumentException("taskId must not be empty");
			}
			if (!taskIds.add(task.getTaskId())) {
				throw new IllegalArgumentException("Duplicate migration task ID: " + task.getTaskId());
			}
			if ((task.getSourceTable() == null) == (task.getKeysetSource() == null)) {
				throw new IllegalArgumentException("Task must have exactly one Table or keyset source: "
						+ task.getTaskId());
			}
			if (task.getOptions() == null || task.getOptions().getMigrationId() == null
					|| task.getOptions().getMigrationId().isBlank()) {
				throw new IllegalArgumentException("Task options require a migrationId: " + task.getTaskId());
			}
			if (!migrationIds.add(task.getOptions().getMigrationId())) {
				throw new IllegalArgumentException("Duplicate checkpoint migrationId: "
						+ task.getOptions().getMigrationId());
			}
		}
		return TableOrder.CREATE.sort(tasks, BulkMigrationJobExecutor::sourceTable);
	}

	private static Table sourceTable(final BulkMigrationJobTask task) {
		return task.getSourceTable() != null ? task.getSourceTable()
				: task.getKeysetSource().getTable();
	}
}
