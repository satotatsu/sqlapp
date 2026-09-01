/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.time.Instant;
import java.util.List;

/** Stable JSON summary of post-migration JDBC verification. */
public record BulkMigrationVerificationReport(int formatVersion, Instant generatedAt,
		String planFingerprint, boolean match, long expectedRows, long actualRows,
		long mismatchedTasks, List<Task> tasks) {
	public static final int CURRENT_FORMAT_VERSION = 1;

	public record Task(String taskId, List<String> columns, boolean match,
			long expectedRows, long actualRows, List<Chunk> mismatches) {
	}

	public record Chunk(long index, int expectedRows, int actualRows,
			String expectedHash, String actualHash) {
	}
}
