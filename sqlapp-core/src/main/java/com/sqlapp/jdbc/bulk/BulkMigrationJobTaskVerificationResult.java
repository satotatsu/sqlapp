/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.List;
import java.util.HashSet;
import java.util.Objects;

import lombok.Value;

/** Verification result for one task in a multi-table migration job. */
@Value
public class BulkMigrationJobTaskVerificationResult {
	String taskId;
	List<String> columns;
	BulkMigrationVerificationResult verificationResult;

	public BulkMigrationJobTaskVerificationResult(final String taskId,
			final List<String> columns,
			final BulkMigrationVerificationResult verificationResult) {
		if (taskId == null || taskId.isBlank()) {
			throw new IllegalArgumentException("taskId must not be empty");
		}
		Objects.requireNonNull(columns, "columns");
		if (columns.isEmpty() || columns.stream()
				.anyMatch(name -> name == null || name.isBlank())) {
			throw new IllegalArgumentException("Verification columns must not be empty: "
					+ taskId);
		}
		if (new HashSet<>(columns).size() != columns.size()) {
			throw new IllegalArgumentException("Verification columns must be unique: "
					+ taskId);
		}
		this.taskId = taskId;
		this.columns = List.copyOf(columns);
		this.verificationResult = Objects.requireNonNull(verificationResult,
				"verificationResult");
		if (!verificationResult.getColumns().isEmpty()
				&& !this.columns.equals(verificationResult.getColumns())) {
			throw new IllegalArgumentException(
					"Task columns must match verification result columns: " + taskId);
		}
	}
}
