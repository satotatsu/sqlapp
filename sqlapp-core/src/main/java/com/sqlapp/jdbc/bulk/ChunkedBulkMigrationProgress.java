/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import lombok.Value;

/** Progress of one migration chunk, using the durable checkpoint counters. */
@Value
public class ChunkedBulkMigrationProgress {
	String migrationId;
	long chunkIndex;
	int chunkRows;
	long processedRowsBefore;
	long processedRowsAfter;

	public ChunkedBulkMigrationProgress(final String migrationId, final long chunkIndex,
			final int chunkRows, final long processedRowsBefore,
			final long processedRowsAfter) {
		BulkMigrationCheckpoint.validateMigrationId(migrationId);
		if (chunkIndex < 0 || chunkRows <= 0 || processedRowsBefore < 0
				|| processedRowsAfter < 0) {
			throw new IllegalArgumentException(
					"chunk progress counts must be non-negative and chunkRows must be positive");
		}
		final long expectedAfter;
		try {
			expectedAfter = Math.addExact(processedRowsBefore, chunkRows);
		} catch (ArithmeticException e) {
			throw new IllegalArgumentException("chunk progress exceeds the supported range", e);
		}
		if (processedRowsAfter != expectedAfter) {
			throw new IllegalArgumentException(
					"processedRowsAfter must equal processedRowsBefore plus chunkRows");
		}
		this.migrationId = migrationId;
		this.chunkIndex = chunkIndex;
		this.chunkRows = chunkRows;
		this.processedRowsBefore = processedRowsBefore;
		this.processedRowsAfter = processedRowsAfter;
	}
}
