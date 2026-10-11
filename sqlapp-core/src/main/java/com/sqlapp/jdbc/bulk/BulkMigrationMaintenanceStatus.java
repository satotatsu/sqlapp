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
	COMPLETE;

	/** Whether explicit lifecycle recovery is required before another execution. */
	public boolean requiresRecovery() {
		return switch (this) {
		case PREPARING, PREPARED, POST_PROCESSING, RESTORING, RESTORE_FAILED -> true;
		case RESTORED, COMPLETE -> false;
		};
	}
}
