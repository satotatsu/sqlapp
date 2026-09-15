/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import com.sqlapp.data.schemas.Table;

import lombok.Builder;
import lombok.Getter;

/** One table migration and its dependency IDs within a job. */
@Getter
@Builder
public class BulkMigrationJobTask {
	private final String taskId;
	private final Table sourceTable;
	private final BulkMigrationKeysetSource keysetSource;
	private final ChunkedBulkMigrationOption options;
	private final BulkMigrationCheckpointStore checkpointStore;
	private final ChunkedBulkMigrationListener chunkListener;

	private BulkMigrationJobTask(final String taskId, final Table sourceTable,
			final BulkMigrationKeysetSource keysetSource,
			final ChunkedBulkMigrationOption options,
			final BulkMigrationCheckpointStore checkpointStore,
			final ChunkedBulkMigrationListener chunkListener) {
		if (taskId == null || taskId.isBlank()) {
			throw new IllegalArgumentException("taskId must not be empty");
		}
		if ((sourceTable == null) == (keysetSource == null)) {
			throw new IllegalArgumentException(
					"Task must have exactly one Table or keyset source: " + taskId);
		}
		this.taskId = taskId;
		this.sourceTable = sourceTable;
		this.keysetSource = keysetSource;
		this.options = java.util.Objects.requireNonNull(options, "options");
		this.checkpointStore = checkpointStore;
		this.chunkListener = chunkListener;
	}
}
