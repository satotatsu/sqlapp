/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.bulk;

import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.bulk.AbstractStagingMigrationSnapshotExecutor;

/** HSQL-specific temporary-table syntax for the shared set-based SCD2 flow. */
public final class HsqlSetBasedMigrationSnapshotExecutor extends AbstractStagingMigrationSnapshotExecutor {
	public HsqlSetBasedMigrationSnapshotExecutor(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected String createStageSql(final String stage, final String target, final List<String> columns) {
		return "DECLARE LOCAL TEMPORARY TABLE " + stage + " AS (SELECT " + list(columns, "t") + " FROM " + target
				+ " t WHERE 1=0) WITH DATA ON COMMIT PRESERVE ROWS";
	}

	@Override
	protected String updateTarget(final String target) {
		return "UPDATE " + target + " t";
	}
}
