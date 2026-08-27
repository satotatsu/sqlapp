/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import com.sqlapp.data.schemas.Table;

import lombok.Builder;
import lombok.Value;

/** One expected table and its prior verification result to repair. */
@Value
@Builder
public class BulkMigrationJobRepairTask {
	String taskId;
	Table expected;
	BulkMigrationVerificationResult verificationResult;
	BulkMigrationRepairOption options;
}
