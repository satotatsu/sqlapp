/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.bulk;

import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.bulk.AbstractStagingMigrationSnapshotExecutor;

/** Oracle 18c+ transaction-private-table SCD2 flow. */
public final class OracleSetBasedMigrationSnapshotExecutor extends AbstractStagingMigrationSnapshotExecutor {
	public OracleSetBasedMigrationSnapshotExecutor(final Dialect dialect) { super(dialect); }
	@Override protected String stageName() { return "ORA$PTT_" + super.stageName(); }
	@Override protected String createStageSql(final String stage, final String target, final List<String> columns) {
		return "CREATE PRIVATE TEMPORARY TABLE " + stage + " ON COMMIT DROP DEFINITION AS SELECT "
				+ list(columns, "t") + " FROM " + target + " t WHERE 1=0";
	}
	@Override protected String updateTarget(final String target) { return "UPDATE " + target + " t"; }
	@Override protected String cleanupStageSql(final String stage) { return null; }
	@Override protected String valuesEqual(final String left, final String right) {
		return "(" + left + " = " + right + " OR (" + left + " IS NULL AND " + right + " IS NULL))";
	}
	@Override protected String valuesDifferent(final String left, final String right) {
		return "(" + left + " <> " + right + " OR (" + left + " IS NULL AND " + right + " IS NOT NULL) OR ("
				+ left + " IS NOT NULL AND " + right + " IS NULL))";
	}
}
