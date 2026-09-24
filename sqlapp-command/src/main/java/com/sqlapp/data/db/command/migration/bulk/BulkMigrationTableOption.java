/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.util.HashSet;
import java.util.List;

import com.sqlapp.jdbc.bulk.BulkUpsertOption;
import com.sqlapp.jdbc.bulk.BulkMigrationRetryOption;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpointStore;

import lombok.Builder;
import lombok.Value;

/** Optional overrides for one table in the simple bulk migration facade. */
@Value
public class BulkMigrationTableOption {
	String migrationId;
	Integer chunkSize;
	Integer verificationChunkSize;
	List<String> keysetColumns;
	List<String> verificationColumns;
	BulkUpsertOption upsertOption;
	BulkOption bulkOption;
	BulkMigrationRetryOption retryOption;
	BulkMigrationCheckpointStore checkpointStore;

	@Builder
	public BulkMigrationTableOption(final String migrationId, final Integer chunkSize,
			final Integer verificationChunkSize, final List<String> keysetColumns,
			final List<String> verificationColumns, final BulkUpsertOption upsertOption, final BulkOption bulkOption,
			final BulkMigrationRetryOption retryOption, final BulkMigrationCheckpointStore checkpointStore) {
		if (migrationId != null && migrationId.isBlank()) {
			throw new IllegalArgumentException("migrationId must not be empty");
		}
		if (chunkSize != null && chunkSize <= 0) {
			throw new IllegalArgumentException("chunkSize must be greater than zero");
		}
		if (verificationChunkSize != null && verificationChunkSize <= 0) {
			throw new IllegalArgumentException("verificationChunkSize must be greater than zero");
		}
		this.migrationId = migrationId;
		this.chunkSize = chunkSize;
		this.verificationChunkSize = verificationChunkSize;
		this.keysetColumns = columns(keysetColumns, "keysetColumns");
		this.verificationColumns = columns(verificationColumns, "verificationColumns");
		this.upsertOption = upsertOption;
		this.bulkOption = bulkOption;
		this.retryOption = retryOption;
		this.checkpointStore = checkpointStore;
	}

	private static List<String> columns(final List<String> values, final String name) {
		final List<String> columns = values == null ? List.of() : List.copyOf(values);
		if (columns.stream().anyMatch(value -> value == null || value.isBlank())
				|| new HashSet<>(columns).size() != columns.size()) {
			throw new IllegalArgumentException(name + " must contain unique non-empty column names");
		}
		return columns;
	}

	public static BulkMigrationTableOption defaults() {
		return builder().build();
	}
}
