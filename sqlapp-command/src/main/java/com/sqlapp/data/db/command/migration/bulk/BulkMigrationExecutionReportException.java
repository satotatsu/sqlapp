/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.util.Objects;

import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.bulk.BulkMigrationJobResult;

/**
 * Raised when migration committed but its requested result file could not be
 * published.
 */
public class BulkMigrationExecutionReportException extends CommandException {
	private static final long serialVersionUID = 1L;
	private final BulkMigrationJobResult migrationResult;

	public BulkMigrationExecutionReportException(final BulkMigrationJobResult migrationResult, final Throwable cause) {
		super("Bulk migration completed, but the execution report could not be published",
				Objects.requireNonNull(cause, "cause"));
		this.migrationResult = Objects.requireNonNull(migrationResult, "migrationResult");
	}

	public BulkMigrationJobResult getMigrationResult() {
		return migrationResult;
	}
}
