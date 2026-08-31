/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.nio.file.Path;
import java.time.Duration;

import com.sqlapp.jdbc.bulk.BulkMigrationJobLease;
import com.sqlapp.jdbc.bulk.BulkMigrationJobLeaseMode;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationJobLeaseStore;

/** Validated configuration for a built-in migration job lease store. */
public record BulkMigrationJobLeaseConfiguration(BulkMigrationJobLeaseMode mode,
		String ownerId, Duration duration, String tableName, Path directory) {
	public static final Duration DEFAULT_DURATION = Duration.ofMinutes(5);

	public BulkMigrationJobLeaseConfiguration {
		if (mode == null) {
			throw new IllegalArgumentException("mode must not be null");
		}
		new BulkMigrationJobLease("validation", ownerId,
				java.time.Instant.MAX);
		if (duration == null || duration.isZero() || duration.isNegative()) {
			throw new IllegalArgumentException("duration must be positive");
		}
		if (mode == BulkMigrationJobLeaseMode.DATABASE
				&& (tableName == null || tableName.isBlank())) {
			throw new IllegalArgumentException(
					"tableName must not be empty for DATABASE lease mode");
		}
		if (mode == BulkMigrationJobLeaseMode.DATABASE && directory != null) {
			throw new IllegalArgumentException(
					"directory is not valid for DATABASE lease mode");
		}
		if (mode == BulkMigrationJobLeaseMode.FILE && directory == null) {
			throw new IllegalArgumentException(
					"directory must not be null for FILE lease mode");
		}
		if (mode == BulkMigrationJobLeaseMode.FILE && tableName != null) {
			throw new IllegalArgumentException(
					"tableName is not valid for FILE lease mode");
		}
		directory = directory == null ? null : directory.toAbsolutePath().normalize();
	}

	public static BulkMigrationJobLeaseConfiguration database(final String ownerId) {
		return new BulkMigrationJobLeaseConfiguration(BulkMigrationJobLeaseMode.DATABASE,
				ownerId, DEFAULT_DURATION,
				JdbcBulkMigrationJobLeaseStore.DEFAULT_TABLE_NAME, null);
	}

	public static BulkMigrationJobLeaseConfiguration file(final String ownerId,
			final Path directory) {
		return new BulkMigrationJobLeaseConfiguration(BulkMigrationJobLeaseMode.FILE,
				ownerId, DEFAULT_DURATION, null, directory);
	}
}
