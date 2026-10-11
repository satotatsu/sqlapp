/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

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

	public BulkMigrationRepairResult(final int mismatchChunks, final int replayedChunks,
			final long replayedRows, final long affectedRows,
			final List<Long> chunksWithExtraActualRows,
			final List<Long> chunksWithoutExpectedRows) {
		if (mismatchChunks < 0 || replayedChunks < 0 || replayedRows < 0 || affectedRows < 0) {
			throw new IllegalArgumentException("Repair result counts must not be negative");
		}
		if (replayedChunks > mismatchChunks) {
			throw new IllegalArgumentException("replayedChunks must not exceed mismatchChunks");
		}
		this.mismatchChunks = mismatchChunks;
		this.replayedChunks = replayedChunks;
		this.replayedRows = replayedRows;
		this.affectedRows = affectedRows;
		this.chunksWithExtraActualRows = chunks(chunksWithExtraActualRows,
				"chunksWithExtraActualRows");
		this.chunksWithoutExpectedRows = chunks(chunksWithoutExpectedRows,
				"chunksWithoutExpectedRows");
	}

	private static List<Long> chunks(final List<Long> values, final String name) {
		Objects.requireNonNull(values, name);
		if (values.stream().anyMatch(value -> value == null || value < 0)
				|| new HashSet<>(values).size() != values.size()) {
			throw new IllegalArgumentException(name
					+ " must contain unique non-negative chunk indexes");
		}
		return List.copyOf(values);
	}

	/** A follow-up verification is always recommended and is required when true. */
	public boolean requiresManualReconciliation() {
		return !chunksWithExtraActualRows.isEmpty() || !chunksWithoutExpectedRows.isEmpty();
	}
}
