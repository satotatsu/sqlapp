/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

/** State derived without changing a task's checkpoint store. */
public enum BulkMigrationJobTaskState {
	NOT_STARTED,
	IN_PROGRESS,
	COMPLETE,
	INCOMPATIBLE
}
