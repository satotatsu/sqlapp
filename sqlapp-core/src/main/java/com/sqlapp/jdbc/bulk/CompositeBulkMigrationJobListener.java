/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/** Dispatches job events to multiple listeners in registration order. */
public final class CompositeBulkMigrationJobListener implements BulkMigrationJobListener {
	private final List<BulkMigrationJobListener> listeners;

	public CompositeBulkMigrationJobListener(
			final List<? extends BulkMigrationJobListener> listeners) {
		Objects.requireNonNull(listeners, "listeners");
		this.listeners = List.copyOf(listeners);
		this.listeners.forEach(listener -> Objects.requireNonNull(listener, "listener"));
	}

	public static CompositeBulkMigrationJobListener of(
			final BulkMigrationJobListener... listeners) {
		return new CompositeBulkMigrationJobListener(List.of(listeners));
	}

	public List<BulkMigrationJobListener> getListeners() {
		return listeners;
	}

	@Override
	public void onJobStarted(final String planFingerprint, final int taskCount) {
		listeners.forEach(listener -> listener.onJobStarted(planFingerprint, taskCount));
	}

	@Override
	public void onJobCompleted(final BulkMigrationJobResult result) {
		listeners.forEach(listener -> listener.onJobCompleted(result));
	}

	@Override
	public void onJobFailed(final String planFingerprint, final Throwable cause) {
		listeners.forEach(listener -> listener.onJobFailed(planFingerprint, cause));
	}

	@Override
	public void onJobPaused(final String planFingerprint, final String taskId,
			final ChunkedBulkMigrationProgress progress) {
		listeners.forEach(listener -> listener.onJobPaused(planFingerprint, taskId, progress));
	}

	@Override
	public void onTaskStarted(final String taskId, final int taskIndex,
			final int taskCount) {
		listeners.forEach(listener -> listener.onTaskStarted(taskId, taskIndex, taskCount));
	}

	@Override
	public void onTaskCompleted(final String taskId,
			final ChunkedBulkMigrationResult result, final int taskIndex,
			final int taskCount) {
		listeners.forEach(listener -> listener.onTaskCompleted(taskId, result,
				taskIndex, taskCount));
	}

	@Override
	public void onTaskFailed(final String taskId, final SQLException cause,
			final int taskIndex, final int taskCount) {
		listeners.forEach(listener -> listener.onTaskFailed(taskId, cause,
				taskIndex, taskCount));
	}

	@Override
	public void onTaskPaused(final String taskId,
			final ChunkedBulkMigrationProgress progress, final int taskIndex,
			final int taskCount) {
		listeners.forEach(listener -> listener.onTaskPaused(taskId, progress,
				taskIndex, taskCount));
	}
}
