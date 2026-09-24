/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Stable JSON snapshot of a reviewed bulk migration repair plan. */
public record BulkMigrationRepairPlanReport(int formatVersion, Instant generatedAt, String planFingerprint,
		Relation source, Relation target, boolean keysetSource, String expectedKeysetFingerprint,
		String actualKeysetFingerprint, String databaseProductName, String databaseProductVersion,
		String executorClassName, boolean atomic, boolean transactionBreakingStaging, String stagingTableName,
		int chunkSize, long estimatedReplayRows, long maxBufferedRows, boolean verifyExpectedHashes,
		List<String> verificationColumns, List<String> keyColumns, List<String> stagingColumns,
		List<String> updateColumns, List<Chunk> mismatchChunks) {
	public static final int CURRENT_FORMAT_VERSION = 1;

	public BulkMigrationRepairPlanReport {
		verificationColumns = copy(verificationColumns, "verificationColumns");
		keyColumns = copy(keyColumns, "keyColumns");
		stagingColumns = copy(stagingColumns, "stagingColumns");
		updateColumns = copy(updateColumns, "updateColumns");
		mismatchChunks = List.copyOf(Objects.requireNonNull(mismatchChunks, "mismatchChunks"));
	}

	private static <T> List<T> copy(final List<T> values, final String name) {
		return List.copyOf(Objects.requireNonNull(values, name));
	}

	public record Relation(String catalogName, String schemaName, String tableName) {
	}

	public record Chunk(long index, int expectedRows, int actualRows, String expectedHash, String actualHash,
			String expectedFirstKey, String expectedLastKey, String actualFirstKey, String actualLastKey) {
	}
}
