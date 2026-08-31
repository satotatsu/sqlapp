/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

import com.sqlapp.jdbc.bulk.BulkMigrationJobLeaseManager;
import com.sqlapp.jdbc.bulk.BulkMigrationJobLeaseStore;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationJobLeaseStore;

/** Creates a lease manager from the validated database-or-file configuration. */
public final class BulkMigrationJobLeaseManagerFactory {
	private BulkMigrationJobLeaseManagerFactory() {
	}

	public static BulkMigrationJobLeaseManager create(final Connection leaseConnection,
			final BulkMigrationJobLeaseConfiguration configuration)
			throws SQLException {
		Objects.requireNonNull(configuration, "configuration");
		final BulkMigrationJobLeaseStore store = switch (configuration.mode()) {
		case DATABASE -> new JdbcBulkMigrationJobLeaseStore(
					Objects.requireNonNull(leaseConnection,
							"leaseConnection is required for DATABASE lease mode"),
					configuration.tableName());
		case FILE -> new FileBulkMigrationJobLeaseStore(configuration.directory());
		};
		return new BulkMigrationJobLeaseManager(store, configuration.ownerId(),
				configuration.duration());
	}
}
