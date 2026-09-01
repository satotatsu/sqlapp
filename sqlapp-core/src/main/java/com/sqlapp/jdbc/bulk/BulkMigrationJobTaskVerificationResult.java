/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.List;

import lombok.Value;

/** Verification result for one task in a multi-table migration job. */
@Value
public class BulkMigrationJobTaskVerificationResult {
	String taskId;
	List<String> columns;
	BulkMigrationVerificationResult verificationResult;

	public BulkMigrationJobTaskVerificationResult(final String taskId,
			final BulkMigrationVerificationResult verificationResult) {
		this(taskId, List.of(), verificationResult);
	}

	public BulkMigrationJobTaskVerificationResult(final String taskId,
			final List<String> columns,
			final BulkMigrationVerificationResult verificationResult) {
		this.taskId = taskId;
		this.columns = List.copyOf(columns);
		this.verificationResult = verificationResult;
	}
}
