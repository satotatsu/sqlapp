/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.time.Instant;
import java.util.Objects;

/** Persisted lifecycle state used to detect interrupted maintenance. */
public record BulkMigrationMaintenanceState(String planFingerprint,
		BulkMigrationMaintenanceStatus status, Instant updatedAt,
		String failureMessage) {
	public BulkMigrationMaintenanceState {
		if (planFingerprint == null || planFingerprint.isBlank()) {
			throw new IllegalArgumentException("planFingerprint must not be empty");
		}
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(updatedAt, "updatedAt");
		if (status != BulkMigrationMaintenanceStatus.RESTORE_FAILED
				&& failureMessage != null) {
			throw new IllegalArgumentException(
					"failureMessage is valid only for RESTORE_FAILED");
		}
		if (status == BulkMigrationMaintenanceStatus.RESTORE_FAILED
				&& (failureMessage == null || failureMessage.isBlank())) {
			throw new IllegalArgumentException(
					"RESTORE_FAILED requires failureMessage");
		}
	}
}
