/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.time.Instant;
import java.util.Objects;

/** Persisted lifecycle state used to detect interrupted maintenance. */
public record BulkMigrationMaintenanceState(String jobId, String planFingerprint,
		BulkMigrationMaintenanceStatus status, Instant updatedAt,
		String failureMessage) {
	public static final int JOB_ID_MAX_LENGTH = 255;
	public static final int FINGERPRINT_MAX_LENGTH = 255;
	public static final int FAILURE_MESSAGE_MAX_LENGTH = 1_000;

	public BulkMigrationMaintenanceState {
		if (jobId == null || jobId.isBlank()) {
			throw new IllegalArgumentException("jobId must not be empty");
		}
		if (jobId.length() > JOB_ID_MAX_LENGTH) {
			throw new IllegalArgumentException("jobId must not exceed "
					+ JOB_ID_MAX_LENGTH + " characters");
		}
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

	public boolean isFor(final BulkMigrationJobPlan plan) {
		Objects.requireNonNull(plan, "plan").validateUnchanged();
		return plan.getJobId().equals(jobId)
				&& plan.getFingerprint().equals(planFingerprint);
	}

	public BulkMigrationMaintenanceState validateAgainst(
			final BulkMigrationJobPlan plan) {
		if (!isFor(plan)) {
			throw new IllegalArgumentException(
					"Maintenance state does not match the migration plan");
		}
		return this;
	}
}
