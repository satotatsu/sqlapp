/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.time.Duration;

/** Operational progress calculated from durable chunk completion events. */
public record BulkMigrationProgressSnapshot(String migrationId,
		long processedRows, Long totalRows, Duration elapsed,
		double rowsPerSecond, Double completionRatio,
		Duration estimatedRemaining) {
	public BulkMigrationProgressSnapshot {
		if (migrationId == null || migrationId.isBlank()) {
			throw new IllegalArgumentException("migrationId must not be empty");
		}
		if (processedRows < 0 || totalRows != null && totalRows < processedRows) {
			throw new IllegalArgumentException("invalid migration row progress");
		}
		if (elapsed == null || elapsed.isNegative()) {
			throw new IllegalArgumentException("elapsed must not be negative");
		}
		if (!Double.isFinite(rowsPerSecond) || rowsPerSecond < 0) {
			throw new IllegalArgumentException("rowsPerSecond must be finite and nonnegative");
		}
		if (completionRatio != null && (!Double.isFinite(completionRatio)
				|| completionRatio < 0 || completionRatio > 1)) {
			throw new IllegalArgumentException("completionRatio must be between zero and one");
		}
		if (estimatedRemaining != null && estimatedRemaining.isNegative()) {
			throw new IllegalArgumentException("estimatedRemaining must not be negative");
		}
	}
}
