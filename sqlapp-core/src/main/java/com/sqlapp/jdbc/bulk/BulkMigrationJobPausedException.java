/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import lombok.Getter;

/** Intentional pause of one task in a multi-table migration job. */
@Getter
public class BulkMigrationJobPausedException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	private final String pausedTaskId;
	private final BulkMigrationJobResult completedResult;
	private final ChunkedBulkMigrationProgress progress;

	public BulkMigrationJobPausedException(final String pausedTaskId,
			final BulkMigrationJobResult completedResult,
			final ChunkedBulkMigrationPausedException cause) {
		super("Migration job paused in task: " + pausedTaskId, cause);
		this.pausedTaskId = pausedTaskId;
		this.completedResult = completedResult;
		this.progress = cause.getProgress();
	}
}
