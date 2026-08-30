/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

/** Phase in which a planned migration operation runs. */
public enum BulkMigrationJobOperationPhase {
	BEFORE,
	AFTER,
	RESTORE
}
