/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.SQLException;
import java.util.Objects;

/** Checks heartbeat health before forward-progress job notifications. */
public final class BulkMigrationJobLeaseJobListener
		implements BulkMigrationJobListener {
	private final BulkMigrationJobLeaseHeartbeat heartbeat;
	private final BulkMigrationJobListener delegate;

	public BulkMigrationJobLeaseJobListener(
			final BulkMigrationJobLeaseHeartbeat heartbeat,
			final BulkMigrationJobListener delegate) {
		this.heartbeat = Objects.requireNonNull(heartbeat, "heartbeat");
		this.delegate = Objects.requireNonNull(delegate, "delegate");
	}

	@Override
	public void onJobStarted(final String planFingerprint, final int taskCount) {
		check();
		delegate.onJobStarted(planFingerprint, taskCount);
	}

	@Override
	public void onJobCompleted(final BulkMigrationJobResult result) {
		check();
		delegate.onJobCompleted(result);
	}

	@Override
	public void onJobFailed(final String planFingerprint, final Throwable cause) {
		delegate.onJobFailed(planFingerprint, cause);
	}

	@Override
	public void onJobPaused(final String planFingerprint, final String taskId,
			final ChunkedBulkMigrationProgress progress) {
		delegate.onJobPaused(planFingerprint, taskId, progress);
	}

	@Override
	public void onTaskStarted(final String taskId, final int taskIndex,
			final int taskCount) {
		check();
		delegate.onTaskStarted(taskId, taskIndex, taskCount);
	}

	@Override
	public void onTaskCompleted(final String taskId,
			final ChunkedBulkMigrationResult result, final int taskIndex,
			final int taskCount) {
		check();
		delegate.onTaskCompleted(taskId, result, taskIndex, taskCount);
	}

	@Override
	public void onTaskFailed(final String taskId, final SQLException cause,
			final int taskIndex, final int taskCount) {
		delegate.onTaskFailed(taskId, cause, taskIndex, taskCount);
	}

	@Override
	public void onTaskPaused(final String taskId,
			final ChunkedBulkMigrationProgress progress, final int taskIndex,
			final int taskCount) {
		delegate.onTaskPaused(taskId, progress, taskIndex, taskCount);
	}

	private void check() {
		heartbeat.check();
	}
}
