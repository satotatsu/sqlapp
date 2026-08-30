/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Vendor-extensible preparation and restoration around a migration job. */
public interface BulkMigrationJobLifecycle {
	BulkMigrationJobLifecycle NO_OP = new BulkMigrationJobLifecycle() { };

	default String getConfigurationFingerprint() {
		return "none";
	}

	default List<BulkMigrationJobOperation> plan(List<BulkMigrationJobTask> tasks) {
		return List.of();
	}

	default void before(Connection connection, BulkMigrationJobPlan plan)
			throws SQLException {
	}

	default void after(Connection connection, BulkMigrationJobPlan plan,
			BulkMigrationJobResult result) throws SQLException {
	}

	default void restore(Connection connection, BulkMigrationJobPlan plan,
			Throwable failure) throws SQLException {
	}
}
