/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.SQLException;

import lombok.Getter;

/** Failure while deleting one task checkpoint from a migration job. */
@Getter
public class BulkMigrationJobCheckpointResetException extends SQLException {
	private static final long serialVersionUID = 1L;

	private final String failedTaskId;
	private final BulkMigrationJobCheckpointResetResult completedResult;

	public BulkMigrationJobCheckpointResetException(final String failedTaskId,
			final BulkMigrationJobCheckpointResetResult completedResult,
			final SQLException cause) {
		super("Migration job checkpoint reset failed: " + failedTaskId, cause);
		this.failedTaskId = failedTaskId;
		this.completedResult = completedResult;
	}
}
