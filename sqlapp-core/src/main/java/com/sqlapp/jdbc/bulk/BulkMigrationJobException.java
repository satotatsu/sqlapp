/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.SQLException;

import lombok.Getter;

/** Failure of one task in a multi-table migration job. */
@Getter
public class BulkMigrationJobException extends SQLException {
	private static final long serialVersionUID = 1L;

	private final String failedTaskId;
	private final BulkMigrationJobResult completedResult;

	public BulkMigrationJobException(final String failedTaskId,
			final BulkMigrationJobResult completedResult, final SQLException cause) {
		super("Migration job task failed: " + failedTaskId, cause);
		this.failedTaskId = failedTaskId;
		this.completedResult = completedResult;
	}
}
