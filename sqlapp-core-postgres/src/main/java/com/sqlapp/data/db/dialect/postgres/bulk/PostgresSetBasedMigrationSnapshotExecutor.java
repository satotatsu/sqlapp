/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.bulk;

import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.bulk.AbstractStagingMigrationSnapshotExecutor;

/** PostgreSQL-specific temporary-table syntax for the shared set-based SCD2 flow. */
public final class PostgresSetBasedMigrationSnapshotExecutor extends AbstractStagingMigrationSnapshotExecutor {
	public PostgresSetBasedMigrationSnapshotExecutor(final Dialect dialect) { super(dialect); }
	@Override protected String createStageSql(final String stage, final String target, final List<String> columns) {
		return "CREATE TEMPORARY TABLE " + stage + " ON COMMIT DROP AS SELECT " + list(columns, "t")
				+ " FROM " + target + " t WITH NO DATA";
	}
	@Override protected String updateTarget(final String target) { return "UPDATE " + target + " AS t"; }
}
