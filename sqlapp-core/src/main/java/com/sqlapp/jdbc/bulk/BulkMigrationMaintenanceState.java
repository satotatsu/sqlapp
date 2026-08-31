/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.time.Instant;
import java.util.Objects;

/** Persisted lifecycle state used to detect interrupted maintenance. */
public record BulkMigrationMaintenanceState(String planFingerprint,
		BulkMigrationMaintenanceStatus status, Instant updatedAt,
		String failureMessage) {
	public static final int FINGERPRINT_MAX_LENGTH = 255;
	public static final int FAILURE_MESSAGE_MAX_LENGTH = 1_000;

	public BulkMigrationMaintenanceState {
		if (planFingerprint == null || planFingerprint.isBlank()) {
			throw new IllegalArgumentException("planFingerprint must not be empty");
		}
		if (planFingerprint.length() > FINGERPRINT_MAX_LENGTH) {
			throw new IllegalArgumentException("planFingerprint must not exceed "
					+ FINGERPRINT_MAX_LENGTH + " characters");
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
		if (failureMessage != null
				&& failureMessage.length() > FAILURE_MESSAGE_MAX_LENGTH) {
			throw new IllegalArgumentException("failureMessage must not exceed "
					+ FAILURE_MESSAGE_MAX_LENGTH + " characters");
		}
	}
}
