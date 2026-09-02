/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.time.Instant;
import java.util.List;

/** Stable JSON summary of post-migration JDBC verification. */
public record BulkMigrationVerificationReport(int formatVersion, Instant generatedAt,
		String planFingerprint, String isolation, boolean match, long expectedRows, long actualRows,
		long mismatchedTasks, List<Task> tasks) {
	public static final int CURRENT_FORMAT_VERSION = 5;

	public record Task(String taskId, List<String> columns,
			String expectedKeysetFingerprint, String actualKeysetFingerprint, boolean match,
			long expectedRows, long actualRows, long mismatchedChunks,
			List<Chunk> mismatches) {
	}

	public record Chunk(long index, int expectedRows, int actualRows,
			String expectedHash, String actualHash, String expectedFirstKey,
			String expectedLastKey, String actualFirstKey, String actualLastKey) {
	}
}
