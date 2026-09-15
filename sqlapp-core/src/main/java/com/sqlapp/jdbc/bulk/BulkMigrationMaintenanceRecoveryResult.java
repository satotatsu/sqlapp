/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.Objects;

/** Result of explicitly recovering interrupted migration maintenance. */
public record BulkMigrationMaintenanceRecoveryResult(
		String jobId,
		String planFingerprint,
		BulkMigrationMaintenanceState previousState,
		BulkMigrationMaintenanceState currentState,
		boolean recovered) {
	public BulkMigrationMaintenanceRecoveryResult {
		if (jobId == null || jobId.isBlank()) {
			throw new IllegalArgumentException("jobId must not be empty");
		}
		if (planFingerprint == null || planFingerprint.isBlank()) {
			throw new IllegalArgumentException("planFingerprint must not be empty");
		}
		if (previousState == null || currentState == null) {
			if (previousState != null || currentState != null || recovered) {
				throw new IllegalArgumentException(
						"maintenance recovery states must both be present or both be absent");
			}
		} else if (!jobId.equals(previousState.jobId())
				|| !planFingerprint.equals(previousState.planFingerprint())
				|| !previousState.jobId().equals(currentState.jobId())
				|| !previousState.planFingerprint().equals(currentState.planFingerprint())) {
			throw new IllegalArgumentException(
					"maintenance recovery states must belong to the result job and plan");
		} else if (recovered) {
			if (!previousState.status().requiresRecovery()
					|| currentState.status() != BulkMigrationMaintenanceStatus.RESTORED) {
				throw new IllegalArgumentException(
						"a recovered result must transition a recoverable state to RESTORED");
			}
		} else if (!previousState.equals(currentState)
				|| previousState.status().requiresRecovery()) {
			throw new IllegalArgumentException(
					"an unrecovered result must retain the same non-recoverable state");
		}
	}

	public BulkMigrationMaintenanceRecoveryResult validateAgainst(
			final BulkMigrationJobPlan plan) {
		Objects.requireNonNull(plan, "plan").validateUnchanged();
		if (!plan.getJobId().equals(jobId)
				|| !plan.getFingerprint().equals(planFingerprint)) {
			throw new IllegalArgumentException(
					"Maintenance recovery result does not match the migration plan");
		}
		return this;
	}
}
