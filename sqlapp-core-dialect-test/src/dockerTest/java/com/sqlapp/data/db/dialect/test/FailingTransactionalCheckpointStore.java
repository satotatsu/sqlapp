/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

import com.sqlapp.jdbc.bulk.BulkMigrationCheckpoint;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpointStore;
import com.sqlapp.jdbc.bulk.TransactionalBulkMigrationCheckpointStore;

/** Injects a checkpoint write failure while retaining the target connection. */
public final class FailingTransactionalCheckpointStore
		implements TransactionalBulkMigrationCheckpointStore {
	private final Connection connection;
	private final BulkMigrationCheckpointStore delegate;

	public FailingTransactionalCheckpointStore(final Connection connection,
			final BulkMigrationCheckpointStore delegate) {
		this.connection = connection;
		this.delegate = delegate;
	}

	@Override
	public boolean participatesIn(final Connection candidate) {
		return connection == candidate;
	}

	@Override
	public Optional<BulkMigrationCheckpoint> load(final String migrationId) throws SQLException {
		return delegate.load(migrationId);
	}

	@Override
	public void save(final BulkMigrationCheckpoint checkpoint) throws SQLException {
		throw new SQLException("simulated checkpoint write failure");
	}

	@Override
	public void delete(final String migrationId) throws SQLException {
		delegate.delete(migrationId);
	}
}
