/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.hsql.Hsql;
import com.sqlapp.jdbc.bulk.SetBasedMigrationSnapshotExecutor;
import com.sqlapp.jdbc.bulk.SetBasedMigrationSnapshotProvider;

/** HSQL staging-table SCD2 provider. */
public class HsqlSetBasedMigrationSnapshotProvider implements SetBasedMigrationSnapshotProvider {
	@Override
	public boolean supports(final Dialect dialect) {
		return dialect instanceof Hsql;
	}

	@Override
	public SetBasedMigrationSnapshotExecutor create(final Dialect dialect) {
		return new HsqlSetBasedMigrationSnapshotExecutor(dialect);
	}
}
