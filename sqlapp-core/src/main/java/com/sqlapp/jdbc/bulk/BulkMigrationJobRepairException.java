/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.SQLException;

import lombok.Getter;

/** SQL or source-consistency failure of one task in a multi-table repair job. */
@Getter
public class BulkMigrationJobRepairException extends SQLException {
	private static final long serialVersionUID = 1L;

	private final String failedTaskId;
	private final BulkMigrationJobRepairResult completedResult;

	public BulkMigrationJobRepairException(final String failedTaskId,
			final BulkMigrationJobRepairResult completedResult, final Throwable cause) {
		super("Migration job repair task failed: " + failedTaskId, cause);
		this.failedTaskId = failedTaskId;
		this.completedResult = completedResult;
	}
}
