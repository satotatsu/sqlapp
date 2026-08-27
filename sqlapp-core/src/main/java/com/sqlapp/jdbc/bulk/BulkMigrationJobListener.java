/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.SQLException;

/** Synchronous lifecycle callbacks for a multi-table migration job. */
public interface BulkMigrationJobListener {
	BulkMigrationJobListener NO_OP = new BulkMigrationJobListener() {
	};

	default void onTaskStarted(final String taskId, final int taskIndex, final int taskCount) {
	}

	default void onTaskCompleted(final String taskId, final ChunkedBulkMigrationResult result,
			final int taskIndex, final int taskCount) {
	}

	default void onTaskFailed(final String taskId, final SQLException cause,
			final int taskIndex, final int taskCount) {
	}
}
