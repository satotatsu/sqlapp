/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

/** Durable state of migration preparation and restoration. */
public enum BulkMigrationMaintenanceStatus {
	PREPARING,
	PREPARED,
	POST_PROCESSING,
	RESTORING,
	RESTORED,
	RESTORE_FAILED,
	COMPLETE
}
