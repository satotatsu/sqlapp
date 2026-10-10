/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.alloydb.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.alloydb.AlloyDB;
import com.sqlapp.data.db.dialect.postgres.bulk.PostgresSetBasedMigrationSnapshotProvider;

/** Reuses PostgreSQL execution with AlloyDB product identity. */
public class AlloyDBSetBasedMigrationSnapshotProvider extends PostgresSetBasedMigrationSnapshotProvider {
	/** Creates the AlloyDB provider. */
	public AlloyDBSetBasedMigrationSnapshotProvider() {
	}

	@Override
	public boolean supports(Dialect dialect) {
		return dialect instanceof AlloyDB;
	}
}
