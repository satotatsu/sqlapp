/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.util.Objects;

import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.bulk.BulkMigrationJobResult;

/**
 * Raised when migration committed but a following verification operation failed.
 */
public class BulkMigrationPostExecutionException extends CommandException {
	private static final long serialVersionUID = 1L;
	private final BulkMigrationJobResult migrationResult;

	public BulkMigrationPostExecutionException(
			final BulkMigrationJobResult migrationResult, final Throwable cause) {
		super("Bulk migration completed, but post-execution verification failed",
				Objects.requireNonNull(cause, "cause"));
		this.migrationResult = Objects.requireNonNull(migrationResult, "migrationResult");
	}

	/** Returns the committed migration result. */
	public BulkMigrationJobResult getMigrationResult() {
		return migrationResult;
	}
}
