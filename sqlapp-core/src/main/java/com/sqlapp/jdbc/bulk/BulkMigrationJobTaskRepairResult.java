/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import lombok.Value;

/** Repair result for one task in a multi-table migration job. */
@Value
public class BulkMigrationJobTaskRepairResult {
	String taskId;
	BulkMigrationRepairResult repairResult;
}
