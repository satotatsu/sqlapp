/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.List;
import java.util.Objects;

/** Dispatches chunk events to multiple listeners in registration order. */
public final class CompositeChunkedBulkMigrationListener
		implements ChunkedBulkMigrationListener {
	private final List<ChunkedBulkMigrationListener> listeners;

	public CompositeChunkedBulkMigrationListener(
			final List<? extends ChunkedBulkMigrationListener> listeners) {
		Objects.requireNonNull(listeners, "listeners");
		this.listeners = List.copyOf(listeners);
		this.listeners.forEach(listener -> Objects.requireNonNull(listener, "listener"));
	}

	public static CompositeChunkedBulkMigrationListener of(
			final ChunkedBulkMigrationListener... listeners) {
		return new CompositeChunkedBulkMigrationListener(List.of(listeners));
	}

	public List<ChunkedBulkMigrationListener> getListeners() {
		return listeners;
	}

	@Override
	public void onChunkStarted(final ChunkedBulkMigrationProgress progress) {
		listeners.forEach(listener -> listener.onChunkStarted(progress));
	}

	@Override
	public void onChunkCompleted(final ChunkedBulkMigrationProgress progress) {
		listeners.forEach(listener -> listener.onChunkCompleted(progress));
	}

	@Override
	public void onChunkFailed(final ChunkedBulkMigrationProgress progress,
			final Throwable cause) {
		listeners.forEach(listener -> listener.onChunkFailed(progress, cause));
	}

	@Override
	public boolean pauseAfterChunk(final ChunkedBulkMigrationProgress progress) {
		boolean pause = false;
		for (final ChunkedBulkMigrationListener listener : listeners) {
			// Invoke every listener even after one requests pause so metrics and
			// coordinated shutdown observers see the durable completion.
			pause |= listener.pauseAfterChunk(progress);
		}
		return pause;
	}
}
