/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.bulk;

import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.bulk.AbstractStagingMigrationSnapshotExecutor;

/** DB2-specific declared-global-temporary-table SCD2 flow. */
public final class Db2SetBasedMigrationSnapshotExecutor extends AbstractStagingMigrationSnapshotExecutor {
	public Db2SetBasedMigrationSnapshotExecutor(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected String stageIdentifier(final String name) {
		return "SESSION." + dialect.quote(name);
	}

	@Override
	protected String createStageSql(final String stage, final String target, final List<String> columns) {
		return "DECLARE GLOBAL TEMPORARY TABLE " + stage + " AS (SELECT " + list(columns, "t") + " FROM " + target
				+ " t) WITH NO DATA ON COMMIT PRESERVE ROWS NOT LOGGED";
	}

	@Override
	protected String updateTarget(final String target) {
		return "UPDATE " + target + " AS t";
	}

	@Override
	protected String cleanupStageSql(final String stage) {
		return "DROP TABLE " + stage;
	}
}
