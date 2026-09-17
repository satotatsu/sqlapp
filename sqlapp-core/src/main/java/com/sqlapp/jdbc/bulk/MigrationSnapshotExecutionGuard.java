/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.SQLException;

/** Final execution fence checked after SCD2 DML and immediately before commit. */
@FunctionalInterface
public interface MigrationSnapshotExecutionGuard {
	MigrationSnapshotExecutionGuard NO_OP = () -> { };

	void check() throws SQLException;
}
