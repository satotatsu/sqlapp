/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.Oracle18c;
import com.sqlapp.jdbc.bulk.SetBasedMigrationSnapshotExecutor;
import com.sqlapp.jdbc.bulk.SetBasedMigrationSnapshotProvider;

/** Oracle 18c+ private-temporary-table SCD2 provider. */
public class OracleSetBasedMigrationSnapshotProvider implements SetBasedMigrationSnapshotProvider {
	@Override public boolean supports(final Dialect dialect) { return dialect instanceof Oracle18c; }
	@Override public SetBasedMigrationSnapshotExecutor create(final Dialect dialect) { return new OracleSetBasedMigrationSnapshotExecutor(dialect); }
}
