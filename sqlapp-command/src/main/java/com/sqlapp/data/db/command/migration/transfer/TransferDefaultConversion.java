/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.util.List;
import java.util.Objects;

import com.sqlapp.data.db.dialect.MigrationDiagnostic;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultValueMigration;

/**
 * Structured report entry for the conversion of one column default in planned
 * target CREATE or ALTER SQL.
 *
 * @param taskId           transfer task
 * @param table            target table name
 * @param column           target column name
 * @param outcome          what happened to the default
 * @param sourceExpression source default as read
 * @param targetExpression target default in the planned SQL, or {@code null}
 *                         when omitted
 * @param differences      semantic differences between source and target
 * @param verification     how far the result was checked
 * @param transferImpact   whether the omitted or changed default affects the
 *                         row copy
 * @param diagnostics      findings; UNRESOLVED ones remain open for cutover
 */
public record TransferDefaultConversion(String taskId, String table, String column,
		DefaultValueMigration.Outcome outcome, String sourceExpression, String targetExpression,
		List<DefaultValueMigration.Difference> differences, DefaultValueMigration.Verification verification,
		TransferImpact transferImpact, List<MigrationDiagnostic> diagnostics) {

	/** How a default affects the row copy of this transfer. */
	public enum TransferImpact {
		/** No rows are copied (schema-only preparation). */
		NOT_APPLICABLE,
		/**
		 * Every copied row supplies the column explicitly; the default only affects
		 * later inserts.
		 */
		INDEPENDENT,
		/** Copied rows rely on the target default. */
		REQUIRES_DEFAULT
	}

	public TransferDefaultConversion {
		Objects.requireNonNull(taskId, "taskId");
		Objects.requireNonNull(table, "table");
		Objects.requireNonNull(column, "column");
		Objects.requireNonNull(outcome, "outcome");
		Objects.requireNonNull(verification, "verification");
		Objects.requireNonNull(transferImpact, "transferImpact");
		differences = List.copyOf(differences);
		diagnostics = List.copyOf(diagnostics);
	}

	/** Whether a finding remains to be corrected or accepted before cutover. */
	public boolean isOpen() {
		return diagnostics.stream().anyMatch(MigrationDiagnostic::isBlocking);
	}
}
