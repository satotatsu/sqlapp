/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import com.sqlapp.data.schemas.Table;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** One table migration and its dependency IDs within a job. */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BulkMigrationJobTask {
	private final String taskId;
	private final Table sourceTable;
	private final BulkMigrationKeysetSource keysetSource;
	private final ChunkedBulkMigrationOption options;
	private final BulkMigrationCheckpointStore checkpointStore;
	private final ChunkedBulkMigrationListener chunkListener;
}
