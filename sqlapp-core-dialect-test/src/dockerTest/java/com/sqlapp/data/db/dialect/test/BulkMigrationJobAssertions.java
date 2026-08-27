/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkMigrationJobExecutor;
import com.sqlapp.jdbc.bulk.BulkMigrationJobListener;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlanner;
import com.sqlapp.jdbc.bulk.BulkMigrationJobStatusInspector;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTask;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTaskState;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationOption;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationResult;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationCheckpointStore;

/** Shared real-database assertions for dependency-ordered migration jobs. */
public final class BulkMigrationJobAssertions {
	private BulkMigrationJobAssertions() {
	}

	public static void assertDependencyOrderAndAggregatedStatus(
			final Connection connection, final Table parent, final Table child)
			throws SQLException {
		final String suffix = UUID.randomUUID().toString();
		final var parentOptions = options("parent-" + suffix);
		final var childOptions = options("child-" + suffix);
		final var store = new JdbcBulkMigrationCheckpointStore(connection,
				parentOptions.getCheckpointTableName());
		final var parentTask = BulkMigrationJobTask.builder().taskId("parent")
				.sourceTable(parent).options(parentOptions).checkpointStore(store).build();
		final var childTask = BulkMigrationJobTask.builder().taskId("child")
				.sourceTable(child).options(childOptions).checkpointStore(store).build();
		final var plan = BulkMigrationJobPlanner.plan(List.of(childTask, parentTask));
		assertEquals(List.of("parent", "child"), plan.getTasks().stream()
				.map(BulkMigrationJobTask::getTaskId).toList());

		final List<String> completed = new ArrayList<>();
		final var result = BulkMigrationJobExecutor.executePlan(connection, plan,
				new BulkMigrationJobListener() {
					@Override
					public void onTaskCompleted(final String taskId,
							final ChunkedBulkMigrationResult migrationResult,
							final int taskIndex, final int taskCount) {
						completed.add(taskIndex + ":" + taskCount + ":" + taskId);
					}
				});
		assertEquals(List.of("0:2:parent", "1:2:child"), completed);
		assertEquals(2, result.getProcessedRows());
		assertEquals(plan.getFingerprint(), result.getPlanFingerprint());

		final var status = BulkMigrationJobStatusInspector.inspect(plan);
		assertEquals(2, status.getCompletedTasks());
		assertEquals(2, status.getProcessedRows());
		assertTrue(status.isCompatible());
		assertTrue(status.getTasks().stream()
				.allMatch(task -> task.getState() == BulkMigrationJobTaskState.COMPLETE));
	}

	private static ChunkedBulkMigrationOption options(final String migrationId) {
		return ChunkedBulkMigrationOption.builder().migrationId(migrationId)
				.sourceFingerprint("source-v1").targetFingerprint("target-v1")
				.chunkSize(1).build();
	}
}
