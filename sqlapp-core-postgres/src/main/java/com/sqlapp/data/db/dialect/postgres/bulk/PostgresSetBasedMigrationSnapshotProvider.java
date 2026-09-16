/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.Postgres;
import com.sqlapp.jdbc.bulk.SetBasedMigrationSnapshotExecutor;
import com.sqlapp.jdbc.bulk.SetBasedMigrationSnapshotProvider;

/** PostgreSQL staging-table SCD2 provider. */
public class PostgresSetBasedMigrationSnapshotProvider implements SetBasedMigrationSnapshotProvider {
	@Override public boolean supports(final Dialect dialect) { return dialect instanceof Postgres; }
	@Override public SetBasedMigrationSnapshotExecutor create(final Dialect dialect) {
		return new PostgresSetBasedMigrationSnapshotExecutor(dialect);
	}
}
