/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.SQLException;
import java.util.Optional;

/** Persistence SPI for migration lifecycle state. */
public interface BulkMigrationMaintenanceStateStore {
	Optional<BulkMigrationMaintenanceState> load(String planFingerprint)
			throws SQLException;

	void save(BulkMigrationMaintenanceState state) throws SQLException;

	void delete(String planFingerprint) throws SQLException;
}
