/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.List;

import lombok.Value;

/** Summary of mismatch chunks replayed through the bulk UPSERT provider. */
@Value
public class BulkMigrationRepairResult {
	int mismatchChunks;
	int replayedChunks;
	long replayedRows;
	long affectedRows;
	List<Long> chunksWithExtraActualRows;
	List<Long> chunksWithoutExpectedRows;

	/** A follow-up verification is always recommended and is required when true. */
	public boolean requiresManualReconciliation() {
		return !chunksWithExtraActualRows.isEmpty() || !chunksWithoutExpectedRows.isEmpty();
	}
}
