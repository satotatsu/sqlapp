/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Thread-safe non-durable checkpoint store, useful for embedding and tests. */
public class InMemoryBulkMigrationCheckpointStore implements BulkMigrationCheckpointStore {
	private final ConcurrentMap<String, BulkMigrationCheckpoint> checkpoints = new ConcurrentHashMap<>();

	@Override
	public Optional<BulkMigrationCheckpoint> load(final String migrationId) throws SQLException {
		BulkMigrationCheckpoint.validateMigrationId(migrationId);
		return Optional.ofNullable(checkpoints.get(migrationId));
	}

	@Override
	public void save(final BulkMigrationCheckpoint checkpoint) throws SQLException {
		final BulkMigrationCheckpoint validated = java.util.Objects
				.requireNonNull(checkpoint, "checkpoint").validate();
		checkpoints.put(validated.getMigrationId(), validated);
	}

	@Override
	public void delete(final String migrationId) throws SQLException {
		BulkMigrationCheckpoint.validateMigrationId(migrationId);
		checkpoints.remove(migrationId);
	}
}
