/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.util.concurrent.atomic.AtomicReference;

import com.sqlapp.jdbc.bulk.BulkMigrationCancellationListener;
import com.sqlapp.jdbc.bulk.BulkMigrationCancellationToken;
import com.sqlapp.jdbc.bulk.BulkMigrationJobListener;
import com.sqlapp.jdbc.bulk.BulkMigrationJobResult;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationListener;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationProgress;

/** A bounded progress snapshot shared by the worker and Swing event thread. */
final class DataMigrationExecutionControl {
	record Progress(String phase, String migrationId, long checkpointRows, long completedChunks,
			boolean pauseAvailable) {
	}

	private final BulkMigrationCancellationToken token = new BulkMigrationCancellationToken();
	private final BulkMigrationCancellationListener cancellation = new BulkMigrationCancellationListener(token);
	private final AtomicReference<Progress> progress = new AtomicReference<>(
			new Progress("Checking reviewed configuration and connections…", "", 0, 0, false));
	private volatile boolean resumable;

	Progress progress() {
		return progress.get();
	}

	boolean pauseRequested() {
		return token.isCancellationRequested();
	}

	boolean requestPause() {
		return progress.get().pauseAvailable() && token.requestCancellation("Paused from desktop");
	}

	void configurePause(final boolean resumable) {
		this.resumable = resumable;
	}

	void finish() {
		progress.updateAndGet(previous -> new Progress("Finished", previous.migrationId(), previous.checkpointRows(),
				previous.completedChunks(), false));
	}

	BulkMigrationJobListener jobListener() {
		return new BulkMigrationJobListener() {
			@Override
			public void onTaskStarted(final String taskId, final int taskIndex, final int taskCount) {
				progress.set(new Progress("Copying task " + (taskIndex + 1) + "/" + taskCount + ": " + taskId, "", 0, 0,
						resumable));
			}

			@Override
			public void onJobCompleted(final BulkMigrationJobResult result) {
				progress.updateAndGet(previous -> new Progress("Copy complete; publishing reports and verifying…",
						previous.migrationId(), previous.checkpointRows(), previous.completedChunks(), false));
			}
		};
	}

	ChunkedBulkMigrationListener chunkListener() {
		return new ChunkedBulkMigrationListener() {
			@Override
			public void onChunkCompleted(final ChunkedBulkMigrationProgress completed) {
				progress.updateAndGet(previous -> new Progress(previous.phase(), completed.getMigrationId(),
						completed.getProcessedRowsAfter(), completed.getChunkIndex() + 1, resumable));
			}

			@Override
			public boolean pauseAfterChunk(final ChunkedBulkMigrationProgress completed) {
				return resumable && cancellation.pauseAfterChunk(completed);
			}
		};
	}
}
