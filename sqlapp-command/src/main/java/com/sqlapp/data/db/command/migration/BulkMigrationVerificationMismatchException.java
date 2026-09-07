/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.util.Objects;

import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.bulk.BulkMigrationJobResult;
import com.sqlapp.jdbc.bulk.BulkMigrationJobVerificationResult;

/** Raised when a caller explicitly requires a matching migration verification. */
public class BulkMigrationVerificationMismatchException extends CommandException {
	private static final long serialVersionUID = 1L;
	private final BulkMigrationJobResult migrationResult;
	private final BulkMigrationJobVerificationResult verificationResult;

	public BulkMigrationVerificationMismatchException(
			final BulkMigrationJobVerificationResult verificationResult) {
		this(null, verificationResult);
	}

	public BulkMigrationVerificationMismatchException(
			final BulkMigrationJobResult migrationResult,
			final BulkMigrationJobVerificationResult verificationResult) {
		super(message(verificationResult));
		this.migrationResult = migrationResult;
		this.verificationResult = verificationResult;
	}

	/** Returns whether this mismatch followed migration execution. */
	public boolean hasMigrationResult() {
		return migrationResult != null;
	}

	/** Returns the committed migration result, or null for verification-only calls. */
	public BulkMigrationJobResult getMigrationResult() {
		return migrationResult;
	}

	public BulkMigrationJobVerificationResult getVerificationResult() {
		return verificationResult;
	}

	private static String message(
			final BulkMigrationJobVerificationResult verificationResult) {
		final var value = Objects.requireNonNull(verificationResult,
				"verificationResult");
		return "Bulk migration verification failed: mismatchedTasks="
				+ value.getMismatchedTasks() + ", expectedRows="
				+ value.getExpectedRows() + ", actualRows=" + value.getActualRows();
	}
}
