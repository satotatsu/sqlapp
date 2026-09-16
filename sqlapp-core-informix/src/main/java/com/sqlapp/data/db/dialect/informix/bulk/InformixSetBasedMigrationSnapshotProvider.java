/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.informix.Informix;
import com.sqlapp.jdbc.bulk.SetBasedMigrationSnapshotExecutor;
import com.sqlapp.jdbc.bulk.SetBasedMigrationSnapshotProvider;

/** Informix session-temporary-table SCD2 provider. */
public class InformixSetBasedMigrationSnapshotProvider implements SetBasedMigrationSnapshotProvider {
	@Override
	public boolean supports(final Dialect dialect) {
		return dialect instanceof Informix;
	}

	@Override
	public SetBasedMigrationSnapshotExecutor create(final Dialect dialect) {
		return new InformixSetBasedMigrationSnapshotExecutor(dialect);
	}
}
