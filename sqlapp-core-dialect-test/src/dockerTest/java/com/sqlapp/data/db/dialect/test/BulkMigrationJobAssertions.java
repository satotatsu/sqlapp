/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpointMode;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpointStore;
import com.sqlapp.jdbc.bulk.BulkMigrationJobExecutor;
import com.sqlapp.jdbc.bulk.BulkMigrationJobListener;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlanner;
import com.sqlapp.jdbc.bulk.BulkMigrationJobStatusInspector;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTask;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTaskState;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationOption;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationResult;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationCheckpointStore;
import com.sqlapp.jdbc.bulk.BulkMigrationJobLease;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationJobLeaseStore;

/** Shared real-database assertions for dependency-ordered migration jobs. */
public final class BulkMigrationJobAssertions {
	private BulkMigrationJobAssertions() {
	}

	/**
	 * Verifies that a JDBC-backed job lease fences owners across independent
	 * database connections and permits takeover only after expiry.
	 */
	public static void assertJdbcLeaseOwnerFencing(final Connection firstConnection,
			final Connection secondConnection) throws SQLException {
		final String suffix = UUID.randomUUID().toString().replace("-", "");
		final String tableName = "SQLAPP_BJL_" + suffix.substring(0, 12);
		final String fingerprint = "plan-" + suffix;
		final Instant acquiredAt = Instant.parse("2026-01-01T00:00:00Z");
		final Duration duration = Duration.ofMinutes(5);
		final var first = new JdbcBulkMigrationJobLeaseStore(firstConnection,
				tableName);
		final var second = new JdbcBulkMigrationJobLeaseStore(secondConnection,
				tableName);
		final var firstLease = new BulkMigrationJobLease(fingerprint,
				"owner-first", acquiredAt.plus(duration));
		assertTrue(first.tryAcquire(firstLease, acquiredAt));
		assertFalse(second.tryAcquire(new BulkMigrationJobLease(fingerprint,
				"owner-second", acquiredAt.plus(duration).plusSeconds(1)),
				acquiredAt.plusSeconds(1)));

		final Instant takeoverAt = acquiredAt.plus(duration).plusSeconds(1);
		final var secondLease = new BulkMigrationJobLease(fingerprint,
				"owner-second", takeoverAt.plus(duration));
		assertTrue(second.tryAcquire(secondLease, takeoverAt));
		first.release(fingerprint, "owner-first");
		assertEquals("owner-second", first.load(fingerprint).orElseThrow().ownerId());
		second.release(fingerprint, "owner-second");
		assertTrue(first.load(fingerprint).isEmpty());
	}

	public static void assertDependencyOrderAndAggregatedStatus(
			final Connection connection, final Table parent, final Table child)
			throws SQLException {
		final var store = new JdbcBulkMigrationCheckpointStore(connection,
				ChunkedBulkMigrationOption.builder().build().getCheckpointTableName());
		assertDependencyOrderAndAggregatedStatus(connection, parent, child, store,
				BulkMigrationCheckpointMode.DATABASE);
	}

	public static void assertDependencyOrderAndAggregatedStatus(
			final Connection connection, final Table parent, final Table child,
			final BulkMigrationCheckpointStore store,
			final BulkMigrationCheckpointMode checkpointMode) throws SQLException {
		final String suffix = UUID.randomUUID().toString();
		final var parentOptions = options("parent-" + suffix, checkpointMode);
		final var childOptions = options("child-" + suffix, checkpointMode);
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

	private static ChunkedBulkMigrationOption options(final String migrationId,
			final BulkMigrationCheckpointMode checkpointMode) {
		return ChunkedBulkMigrationOption.builder().migrationId(migrationId)
				.sourceFingerprint("source-v1").targetFingerprint("target-v1")
				.checkpointMode(checkpointMode).chunkSize(1).build();
	}
}
