/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.List;

import lombok.Value;

/** Full count and chunk-hash verification result. */
@Value
public class BulkMigrationVerificationResult {
	long expectedRows;
	long actualRows;
	List<BulkMigrationVerificationChunk> chunks;

	public boolean isMatch() {
		return expectedRows == actualRows
				&& chunks.stream().allMatch(BulkMigrationVerificationChunk::isMatch);
	}

	public List<BulkMigrationVerificationChunk> getMismatches() {
		return chunks.stream().filter(chunk -> !chunk.isMatch()).toList();
	}
}
