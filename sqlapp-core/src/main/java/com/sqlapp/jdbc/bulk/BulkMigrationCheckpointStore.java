/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.SQLException;
import java.util.Optional;

/** Persistence extension point for resumable migration progress. */
public interface BulkMigrationCheckpointStore {
	Optional<BulkMigrationCheckpoint> load(String migrationId) throws SQLException;
	void save(BulkMigrationCheckpoint checkpoint) throws SQLException;
	void delete(String migrationId) throws SQLException;
}
