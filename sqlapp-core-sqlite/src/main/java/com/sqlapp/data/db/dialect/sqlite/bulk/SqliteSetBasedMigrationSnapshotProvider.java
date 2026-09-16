/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.sqlite.Sqlite;
import com.sqlapp.jdbc.bulk.SetBasedMigrationSnapshotExecutor;
import com.sqlapp.jdbc.bulk.SetBasedMigrationSnapshotProvider;

/** SQLite staging-table SCD2 provider. */
public class SqliteSetBasedMigrationSnapshotProvider implements SetBasedMigrationSnapshotProvider {
	@Override public boolean supports(final Dialect dialect) { return dialect instanceof Sqlite; }
	@Override public SetBasedMigrationSnapshotExecutor create(final Dialect dialect) {
		return new SqliteSetBasedMigrationSnapshotExecutor(dialect);
	}
}
