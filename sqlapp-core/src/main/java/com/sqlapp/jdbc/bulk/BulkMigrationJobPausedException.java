/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.Objects;

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
		if (pausedTaskId == null || pausedTaskId.isBlank()) {
			throw new IllegalArgumentException("pausedTaskId must not be empty");
		}
		this.pausedTaskId = pausedTaskId;
		this.completedResult = Objects.requireNonNull(completedResult, "completedResult");
		this.progress = Objects.requireNonNull(cause, "cause").getProgress();
	}
}
