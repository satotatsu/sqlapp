/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.io.Serializable;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** Options for a resumable chunk migration. */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChunkedBulkMigrationOption implements Serializable {
	private static final long serialVersionUID = 1L;
	private final String migrationId;
	@Builder.Default
	private final int chunkSize = 10_000;
	@Builder.Default
	private final BulkMigrationMode mode = BulkMigrationMode.UPSERT;
	@Builder.Default
	private final boolean resume = true;
	@Builder.Default
	private final BulkMigrationCheckpointMode checkpointMode = BulkMigrationCheckpointMode.DATABASE;
	@Builder.Default
	private final String checkpointTableName = "SQLAPP_BULK_MIGRATION_CHECKPOINT";
	private final String sourceFingerprint;
	private final String targetFingerprint;
	@Builder.Default
	private final BulkOption bulkOption = BulkOption.defaults();
	@Builder.Default
	private final BulkUpsertOption bulkUpsertOption = BulkUpsertOption.defaults();

	void validate() {
		if (migrationId == null || migrationId.isBlank()) {
			throw new IllegalArgumentException("migrationId must not be empty");
		}
		if (chunkSize <= 0) {
			throw new IllegalArgumentException("chunkSize must be greater than zero");
		}
		if (mode == null) {
			throw new IllegalArgumentException("mode must not be null");
		}
		if (checkpointMode == null) {
			throw new IllegalArgumentException("checkpointMode must not be null");
		}
		if (checkpointMode == BulkMigrationCheckpointMode.DATABASE
				&& (checkpointTableName == null || checkpointTableName.isBlank())) {
			throw new IllegalArgumentException(
					"checkpointTableName must not be empty for DATABASE checkpoints");
		}
		if (resume && (sourceFingerprint == null || sourceFingerprint.isBlank())) {
			throw new IllegalArgumentException(
					"sourceFingerprint must not be empty when resume is enabled");
		}
		if (resume && (targetFingerprint == null || targetFingerprint.isBlank())) {
			throw new IllegalArgumentException(
					"targetFingerprint must not be empty when resume is enabled");
		}
	}
}
