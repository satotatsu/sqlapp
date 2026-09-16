/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.bulk;

import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.bulk.AbstractStagingMigrationSnapshotExecutor;

/** MySQL-specific temporary-table and null-safe-comparison SCD2 flow. */
public final class MySqlSetBasedMigrationSnapshotExecutor extends AbstractStagingMigrationSnapshotExecutor {
	public MySqlSetBasedMigrationSnapshotExecutor(final Dialect dialect) { super(dialect); }
	@Override protected String createStageSql(final String stage, final String target, final List<String> columns) {
		return "CREATE TEMPORARY TABLE " + stage + " AS SELECT " + list(columns, "t") + " FROM " + target
				+ " t WHERE 1=0";
	}
	@Override protected String updateTarget(final String target) { return "UPDATE " + target + " t"; }
	@Override protected String valuesEqual(final String left, final String right) { return left + " <=> " + right; }
	@Override protected String valuesDifferent(final String left, final String right) { return "NOT (" + left + " <=> " + right + ")"; }
}
