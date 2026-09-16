/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sybase.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.sybase.Sybase;
import com.sqlapp.jdbc.bulk.SetBasedMigrationSnapshotExecutor;
import com.sqlapp.jdbc.bulk.SetBasedMigrationSnapshotProvider;

/** SAP ASE local-temporary-table SCD2 provider. */
public class SybaseSetBasedMigrationSnapshotProvider implements SetBasedMigrationSnapshotProvider {
	@Override
	public boolean supports(final Dialect dialect) {
		return dialect instanceof Sybase;
	}

	@Override
	public SetBasedMigrationSnapshotExecutor create(final Dialect dialect) {
		return new SybaseSetBasedMigrationSnapshotExecutor(dialect);
	}
}
