/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Objects;

/**
 * Structured execution guidance without driver messages, SQL values or URLs.
 */
public record TransferFailureDiagnostic(Kind kind, String sqlState, Integer vendorCode) {

	public enum Kind {
		UNIQUE_CONSTRAINT, FOREIGN_KEY, NOT_NULL, CHECK_CONSTRAINT, CONSTRAINT, VALUE_CONVERSION, PERMISSION,
		AUTHENTICATION, CONNECTION, DATABASE, SCHEMA, SQL_OR_METADATA, UNSUPPORTED, TRANSACTION,
		TIMEOUT_OR_CANCELLATION, VERIFICATION_MISMATCH, REPLACEMENT_TARGET_CHANGED, UNKNOWN
	}

	public TransferFailureDiagnostic {
		Objects.requireNonNull(kind, "kind");
		if (sqlState != null && !sqlState.matches("[A-Za-z0-9]{5}"))
			throw new IllegalArgumentException("SQLState must contain five ASCII letters or digits.");
	}

	static TransferFailureDiagnostic capture(Throwable failure) {
		var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
		var pending = new ArrayDeque<Throwable>();
		if (failure != null)
			pending.add(failure);
		TransferFailureDiagnostic fallback = null;
		while (!pending.isEmpty() && seen.size() < 64) {
			var current = pending.removeFirst();
			if (!seen.add(current))
				continue;
			TransferFailureDiagnostic candidate = null;
			if (current instanceof SQLException sql) {
				candidate = codes(sql.getSQLState(), sql.getErrorCode());
				if (candidate.kind() == Kind.UNKNOWN && sql instanceof SQLTimeoutException)
					candidate = new TransferFailureDiagnostic(Kind.TIMEOUT_OR_CANCELLATION, candidate.sqlState(),
							candidate.vendorCode());
				if (sql.getNextException() != null)
					pending.addLast(sql.getNextException());
			} else if (current instanceof TransferJobPreparationService.PreparationException preparation) {
				candidate = codes(preparation.sqlState(), preparation.vendorCode());
			}
			if (candidate != null) {
				if (candidate.kind() != Kind.UNKNOWN && candidate.kind() != Kind.CONSTRAINT
						&& candidate.kind() != Kind.SQL_OR_METADATA)
					return candidate;
				if (fallback == null || fallback.sqlState() == null
						|| fallback.kind() == Kind.UNKNOWN && candidate.kind() != Kind.UNKNOWN)
					fallback = candidate;
			}
			if (current.getCause() != null)
				pending.addLast(current.getCause());
		}
		return fallback == null ? new TransferFailureDiagnostic(Kind.UNKNOWN, null, null) : fallback;
	}

	static TransferFailureDiagnostic capture(Throwable failure, boolean verificationMismatch) {
		var diagnostic = capture(failure);
		return verificationMismatch && diagnostic.kind() == Kind.UNKNOWN ? new TransferFailureDiagnostic(
				Kind.VERIFICATION_MISMATCH, diagnostic.sqlState(), diagnostic.vendorCode()) : diagnostic;
	}

	private static TransferFailureDiagnostic codes(String raw, Integer vendorCode) {
		String state = raw != null && raw.matches("[A-Za-z0-9]{5}") ? raw.toUpperCase(Locale.ROOT) : null;
		Kind kind = state == null ? Kind.UNKNOWN : switch (state) {
		case "23505" -> Kind.UNIQUE_CONSTRAINT;
		case "23503" -> Kind.FOREIGN_KEY;
		case "23502" -> Kind.NOT_NULL;
		case "23513", "23514" -> Kind.CHECK_CONSTRAINT;
		case "42501" -> Kind.PERMISSION;
		case "HYT00", "HYT01", "57014" -> Kind.TIMEOUT_OR_CANCELLATION;
		default -> switch (state.substring(0, 2)) {
		case "23" -> Kind.CONSTRAINT;
		case "22" -> Kind.VALUE_CONVERSION;
		case "28" -> Kind.AUTHENTICATION;
		case "08" -> Kind.CONNECTION;
		case "3D" -> Kind.DATABASE;
		case "3F" -> Kind.SCHEMA;
		case "42" -> Kind.SQL_OR_METADATA;
		case "0A" -> Kind.UNSUPPORTED;
		case "40" -> Kind.TRANSACTION;
		default -> Kind.UNKNOWN;
		};
		};
		return new TransferFailureDiagnostic(kind, state, vendorCode);
	}

	public String reason() {
		return switch (kind) {
		case UNIQUE_CONSTRAINT -> "A primary key or unique constraint rejected a row.";
		case FOREIGN_KEY -> "A foreign key constraint rejected a row.";
		case NOT_NULL -> "A required target column received NULL.";
		case CHECK_CONSTRAINT -> "A target CHECK constraint rejected a row.";
		case CONSTRAINT -> "A target integrity constraint rejected the operation.";
		case VALUE_CONVERSION -> "A value could not be represented by the target type.";
		case PERMISSION -> "Database permissions are insufficient for the operation.";
		case AUTHENTICATION -> "Database authentication failed.";
		case CONNECTION -> "The database connection failed or was lost.";
		case DATABASE -> "The database selected by the connection was not found.";
		case SCHEMA -> "The selected database schema could not be resolved.";
		case SQL_OR_METADATA -> "The database could not resolve the SQL or referenced objects.";
		case UNSUPPORTED -> "The database or driver does not support the requested operation.";
		case TRANSACTION -> "The database rejected the transaction, for example because of concurrent work.";
		case TIMEOUT_OR_CANCELLATION -> "The database operation timed out or was cancelled.";
		case VERIFICATION_MISMATCH -> "Copied data did not match the source or file package during verification.";
		case REPLACEMENT_TARGET_CHANGED ->
			"The reviewed target structure or foreign-key scope changed before replacement.";
		case UNKNOWN ->
			"No recognized database cause code was available. Inspect the failed phase and retained results.";
		};
	}

	public String technicalDetails() {
		return "SQLState: " + (sqlState == null ? "not provided" : sqlState) + "; vendor code: "
				+ (vendorCode == null ? "not provided" : vendorCode);
	}

	public String nextStep() {
		return switch (kind) {
		case UNIQUE_CONSTRAINT ->
			"Check duplicate source keys, target unique constraints and any rows already committed.";
		case FOREIGN_KEY -> "Check selected parent tables, referenced values and the target foreign-key definitions.";
		case NOT_NULL -> "Check source NULL values and target columns that require a value or default.";
		case CHECK_CONSTRAINT, CONSTRAINT ->
			"Compare source values with the target constraints and inspect committed progress.";
		case VALUE_CONVERSION -> "Check target data types, lengths, numeric precision and temporal precision.";
		case PERMISSION -> "Check table access and permissions for the reviewed Schema SQL.";
		case AUTHENTICATION -> "Check the username/password and test the connection.";
		case CONNECTION, DATABASE ->
			"Test the connection and check the JDBC URL, database availability and file locks.";
		case SCHEMA -> "Check the target schema name and create the namespace if necessary, then review again.";
		case SQL_OR_METADATA, UNSUPPORTED ->
			"Inspect Schema SQL, identifier mappings and the target database/driver capabilities.";
		case TRANSACTION, TIMEOUT_OR_CANCELLATION ->
			"Check locks, concurrent work and timeout settings; inspect committed progress before retrying.";
		case VERIFICATION_MISMATCH ->
			"Inspect the mismatched tables and chunks, conversion warnings and target precision. Copied rows may already be committed.";
		case REPLACEMENT_TARGET_CHANGED ->
			"Review the selected tables and referencing foreign keys again before executing.";
		case UNKNOWN ->
			"Inspect verification, Schema SQL and committed progress; check the database server log if needed.";
		};
	}
}
