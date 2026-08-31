/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.SQLException;

/** Synchronous lifecycle callbacks for resumable migration chunks. */
public interface ChunkedBulkMigrationListener {
	ChunkedBulkMigrationListener NO_OP = new ChunkedBulkMigrationListener() {
	};

	default void onChunkStarted(final ChunkedBulkMigrationProgress progress) {
	}

	default void onChunkCompleted(final ChunkedBulkMigrationProgress progress) {
	}

	default void onChunkFailed(final ChunkedBulkMigrationProgress progress, final Throwable cause) {
	}

	default void onChunkRetry(final ChunkedBulkMigrationProgress progress,
			final SQLException cause, final int retryNumber,
			final long backoffMillis) {
	}

	/** Reports a retry of the final complete-checkpoint transaction. */
	default void onCompletionCheckpointRetry(final String migrationId,
			final SQLException cause, final int retryNumber,
			final long backoffMillis) {
	}

	/** Requests a safe pause after this completed chunk. */
	default boolean pauseAfterChunk(final ChunkedBulkMigrationProgress progress) {
		return false;
	}
}
