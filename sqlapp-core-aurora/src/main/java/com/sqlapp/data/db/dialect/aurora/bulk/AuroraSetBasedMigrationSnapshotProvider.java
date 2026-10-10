/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.aurora.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.aurora.AuroraPostgreSQL;
import com.sqlapp.data.db.dialect.postgres.bulk.PostgresSetBasedMigrationSnapshotProvider;

/** Reuses PostgreSQL execution with Aurora product identity. */
public class AuroraSetBasedMigrationSnapshotProvider extends PostgresSetBasedMigrationSnapshotProvider {
	@Override
	public boolean supports(Dialect dialect) {
		return dialect instanceof AuroraPostgreSQL;
	}
}
