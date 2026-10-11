/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.sqlserver.SqlServer2000;
import com.sqlapp.jdbc.bulk.SetBasedMigrationSnapshotExecutor;
import com.sqlapp.jdbc.bulk.SetBasedMigrationSnapshotProvider;

/** SQL Server temporary-table SCD2 provider. */
public class SqlServerSetBasedMigrationSnapshotProvider implements SetBasedMigrationSnapshotProvider {
	@Override
	public boolean supports(final Dialect dialect) {
		return dialect instanceof SqlServer2000;
	}

	@Override
	public SetBasedMigrationSnapshotExecutor create(final Dialect dialect) {
		return new SqlServerSetBasedMigrationSnapshotExecutor(dialect);
	}
}
