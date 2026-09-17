/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.time.Duration;

import com.sqlapp.jdbc.bulk.BulkMigrationJobLeaseMode;

/** Resolved operational lease settings recorded in snapshot audit artifacts. */
public record MigrationSnapshotLeaseEvidence(BulkMigrationJobLeaseMode mode, String ownerId, Duration duration,
		String tableName, String directory) {
	public MigrationSnapshotLeaseEvidence {
		if (mode == null || ownerId == null || ownerId.isBlank() || duration == null
				|| duration.isZero() || duration.isNegative()) {
			throw new IllegalArgumentException("Snapshot lease evidence requires mode, ownerId, and positive duration");
		}
		if (mode == BulkMigrationJobLeaseMode.DATABASE
				&& (tableName == null || tableName.isBlank() || directory != null)) {
			throw new IllegalArgumentException("DATABASE snapshot lease evidence requires only tableName");
		}
		if (mode == BulkMigrationJobLeaseMode.FILE
				&& (directory == null || directory.isBlank() || tableName != null)) {
			throw new IllegalArgumentException("FILE snapshot lease evidence requires only directory");
		}
	}

	public static MigrationSnapshotLeaseEvidence from(final BulkMigrationJobLeaseConfiguration configuration) {
		if (configuration == null) return null;
		return new MigrationSnapshotLeaseEvidence(configuration.mode(), configuration.ownerId(),
				configuration.duration(), configuration.tableName(),
				configuration.directory() == null ? null : configuration.directory().toString());
	}
}
