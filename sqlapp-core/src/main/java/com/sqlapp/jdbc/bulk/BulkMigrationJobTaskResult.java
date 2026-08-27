/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import lombok.Value;

/** Result of one table task in dependency execution order. */
@Value
public class BulkMigrationJobTaskResult {
	String taskId;
	ChunkedBulkMigrationResult migrationResult;
}
