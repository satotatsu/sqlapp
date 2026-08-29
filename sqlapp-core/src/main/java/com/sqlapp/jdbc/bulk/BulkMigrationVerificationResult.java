/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.List;
import java.util.Objects;

import lombok.Value;

/** Full count and chunk-hash verification result. */
@Value
public class BulkMigrationVerificationResult {
	int chunkSize;
	long expectedRows;
	long actualRows;
	List<BulkMigrationVerificationChunk> chunks;

	public BulkMigrationVerificationResult(final int chunkSize, final long expectedRows,
			final long actualRows, final List<BulkMigrationVerificationChunk> chunks) {
		if (chunkSize <= 0) {
			throw new IllegalArgumentException("chunkSize must be greater than zero");
		}
		if (expectedRows < 0 || actualRows < 0) {
			throw new IllegalArgumentException("row counts must not be negative");
		}
		this.chunkSize = chunkSize;
		this.expectedRows = expectedRows;
		this.actualRows = actualRows;
		this.chunks = List.copyOf(Objects.requireNonNull(chunks, "chunks"));
	}

	public boolean isMatch() {
		return expectedRows == actualRows
				&& chunks.stream().allMatch(BulkMigrationVerificationChunk::isMatch);
	}

	public List<BulkMigrationVerificationChunk> getMismatches() {
		return chunks.stream().filter(chunk -> !chunk.isMatch()).toList();
	}
}
