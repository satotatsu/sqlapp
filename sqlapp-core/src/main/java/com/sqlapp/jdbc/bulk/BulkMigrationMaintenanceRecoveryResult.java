/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

/** Result of explicitly recovering interrupted migration maintenance. */
public record BulkMigrationMaintenanceRecoveryResult(
		BulkMigrationMaintenanceState previousState,
		BulkMigrationMaintenanceState currentState,
		boolean recovered) {
}
