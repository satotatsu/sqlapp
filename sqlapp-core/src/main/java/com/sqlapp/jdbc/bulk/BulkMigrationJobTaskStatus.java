/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import lombok.Value;

/** Checkpoint-derived status for one planned migration task. */
@Value
public class BulkMigrationJobTaskStatus {
	String taskId;
	BulkMigrationJobTaskState state;
	BulkMigrationCheckpoint checkpoint;
}
