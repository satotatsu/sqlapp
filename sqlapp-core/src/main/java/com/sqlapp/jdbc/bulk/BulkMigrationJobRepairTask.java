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
	BulkMigrationVerificationResult verificationResult;
	@Builder.Default
	BulkMigrationRepairOption options = BulkMigrationRepairOption.defaults();

	Table getExpectedTable() {
		return expected != null ? expected
				: expectedKeysetSource == null ? null : expectedKeysetSource.getTable();
	}
}
