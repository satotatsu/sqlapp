/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import lombok.Value;

/** Result of one invocation, including rows skipped from an earlier run. */
@Value
public class ChunkedBulkMigrationResult {
	long previouslyProcessedRows;
	long processedRows;
	long completedChunks;
	boolean alreadyComplete;

	public ChunkedBulkMigrationResult(final long previouslyProcessedRows,
			final long processedRows, final long completedChunks,
			final boolean alreadyComplete) {
		if (previouslyProcessedRows < 0 || processedRows < 0 || completedChunks < 0) {
			throw new IllegalArgumentException("Migration result counts must not be negative");
		}
		this.previouslyProcessedRows = previouslyProcessedRows;
		this.processedRows = processedRows;
		this.completedChunks = completedChunks;
		this.alreadyComplete = alreadyComplete;
	}
}
