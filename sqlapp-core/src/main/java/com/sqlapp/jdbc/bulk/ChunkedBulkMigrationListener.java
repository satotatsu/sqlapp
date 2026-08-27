/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

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
}
