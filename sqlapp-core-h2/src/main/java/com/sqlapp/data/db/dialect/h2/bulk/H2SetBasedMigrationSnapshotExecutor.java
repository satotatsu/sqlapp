/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.bulk;

import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.bulk.AbstractStagingMigrationSnapshotExecutor;

/** H2-specific local-temporary-table SCD2 flow. */
public final class H2SetBasedMigrationSnapshotExecutor extends AbstractStagingMigrationSnapshotExecutor {
	public H2SetBasedMigrationSnapshotExecutor(final Dialect dialect) { super(dialect); }
	@Override protected String stageIdentifier(final String name) { return name; }
	@Override protected String createStageSql(final String stage, final String target, final List<String> columns) {
		return "CREATE LOCAL TEMPORARY TABLE " + stage + " AS SELECT " + list(columns, "t") + " FROM " + target + " t WHERE 1=0";
	}
	@Override protected String updateTarget(final String target) { return "UPDATE " + target + " t"; }
}
