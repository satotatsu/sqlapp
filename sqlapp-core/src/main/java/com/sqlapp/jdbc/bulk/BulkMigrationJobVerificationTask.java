/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import com.sqlapp.data.schemas.Table;

import lombok.Builder;
import lombok.Value;

/** Expected and actual row streams for one job task verification. */
@Value
@Builder
public class BulkMigrationJobVerificationTask {
	String taskId;
	Table expected;
	Table actual;
	int chunkSize;
}
