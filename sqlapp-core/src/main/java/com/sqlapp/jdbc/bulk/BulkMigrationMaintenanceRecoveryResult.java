/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

/** Result of explicitly recovering interrupted migration maintenance. */
public record BulkMigrationMaintenanceRecoveryResult(
		BulkMigrationMaintenanceState previousState,
		BulkMigrationMaintenanceState currentState,
		boolean recovered) {
	public BulkMigrationMaintenanceRecoveryResult {
		if (previousState == null || currentState == null) {
			if (previousState != null || currentState != null || recovered) {
				throw new IllegalArgumentException(
						"maintenance recovery states must both be present or both be absent");
			}
		} else if (!previousState.jobId().equals(currentState.jobId())
				|| !previousState.planFingerprint()
						.equals(currentState.planFingerprint())) {
			throw new IllegalArgumentException(
					"maintenance recovery states must belong to the same job and plan");
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
}
