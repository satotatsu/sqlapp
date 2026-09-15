/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import com.sqlapp.data.schemas.Table;

import lombok.Builder;
import lombok.Value;

/** One materialized or keyset-backed expected table and its prior verification result. */
@Value
@Builder
public class BulkMigrationJobRepairTask {
	String taskId;
	Table expected;
	BulkMigrationKeysetSource expectedKeysetSource;
	/** Optional target identity/schema when it differs from the expected source. */
	Table target;
	BulkMigrationVerificationResult verificationResult;
	@Builder.Default
	BulkMigrationRepairOption options = BulkMigrationRepairOption.defaults();

	private BulkMigrationJobRepairTask(final String taskId, final Table expected,
			final BulkMigrationKeysetSource expectedKeysetSource, final Table target,
			final BulkMigrationVerificationResult verificationResult,
			final BulkMigrationRepairOption options) {
		if (taskId == null || taskId.isBlank()) {
			throw new IllegalArgumentException("taskId must not be empty");
		}
		if ((expected == null) == (expectedKeysetSource == null)) {
			throw new IllegalArgumentException("Exactly one of expected or expectedKeysetSource "
					+ "is required for task " + taskId);
		}
		this.taskId = taskId;
		this.expected = expected;
		this.expectedKeysetSource = expectedKeysetSource;
		this.target = target;
		this.verificationResult = java.util.Objects.requireNonNull(verificationResult,
				"verificationResult");
		this.options = java.util.Objects.requireNonNull(options, "options");
	}

	Table getExpectedTable() {
		return expected != null ? expected
				: expectedKeysetSource == null ? null : expectedKeysetSource.getTable();
	}

	Table getTargetTable() {
		return target != null ? target : getExpectedTable();
	}
}
