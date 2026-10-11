/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;

/** Marker for a checkpoint store participating in the target JDBC transaction. */
public interface TransactionalBulkMigrationCheckpointStore extends BulkMigrationCheckpointStore {
	boolean participatesIn(Connection connection);
}
