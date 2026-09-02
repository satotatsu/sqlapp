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

	Table getExpectedTable() {
		return expected != null ? expected
				: expectedKeysetSource == null ? null : expectedKeysetSource.getTable();
	}

	Table getTargetTable() {
		return target != null ? target : getExpectedTable();
	}
}
