/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.io.Serializable;

import lombok.Builder;
import lombok.Getter;

/** Options for a resumable chunk migration. */
@Getter
@Builder
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
	@Builder.Default
	private final BulkMigrationRetryOption retryOption = BulkMigrationRetryOption.none();

	private ChunkedBulkMigrationOption(final String migrationId, final int chunkSize,
			final BulkMigrationMode mode, final boolean resume,
			final BulkMigrationCheckpointMode checkpointMode,
			final String checkpointTableName, final String sourceFingerprint,
			final String targetFingerprint, final BulkOption bulkOption,
			final BulkUpsertOption bulkUpsertOption,
			final BulkMigrationRetryOption retryOption) {
		this.migrationId = migrationId;
		this.chunkSize = chunkSize;
		this.mode = mode;
		this.resume = resume;
		this.checkpointMode = checkpointMode;
		this.checkpointTableName = checkpointTableName;
		this.sourceFingerprint = sourceFingerprint;
		this.targetFingerprint = targetFingerprint;
		this.bulkOption = bulkOption;
		this.bulkUpsertOption = bulkUpsertOption;
		this.retryOption = retryOption;
		validateStructure();
	}

	void validate() {
		validateStructure();
		if (resume && (sourceFingerprint == null || sourceFingerprint.isBlank())) {
			throw new IllegalArgumentException(
					"sourceFingerprint must not be empty when resume is enabled");
		}
		if (resume && (targetFingerprint == null || targetFingerprint.isBlank())) {
			throw new IllegalArgumentException(
					"targetFingerprint must not be empty when resume is enabled");
		}
	}

	private void validateStructure() {
		BulkMigrationCheckpoint.validateMigrationId(migrationId);
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
		if (sourceFingerprint != null
				&& sourceFingerprint.length() > BulkMigrationCheckpoint.FINGERPRINT_MAX_LENGTH) {
			throw new IllegalArgumentException("sourceFingerprint must not exceed "
					+ BulkMigrationCheckpoint.FINGERPRINT_MAX_LENGTH + " characters");
		}
		if (targetFingerprint != null
				&& targetFingerprint.length() > BulkMigrationCheckpoint.FINGERPRINT_MAX_LENGTH) {
			throw new IllegalArgumentException("targetFingerprint must not exceed "
					+ BulkMigrationCheckpoint.FINGERPRINT_MAX_LENGTH + " characters");
		}
		java.util.Objects.requireNonNull(bulkOption, "bulkOption");
		java.util.Objects.requireNonNull(bulkUpsertOption, "bulkUpsertOption");
		java.util.Objects.requireNonNull(retryOption, "retryOption");
		retryOption.validate();
	}
}
