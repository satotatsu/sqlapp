/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.bulk;

import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.bulk.AbstractStagingMigrationSnapshotExecutor;

/** DuckDB temporary-table staging for set-based SCD2 snapshots. */
public final class DuckDbSetBasedMigrationSnapshotExecutor extends AbstractStagingMigrationSnapshotExecutor {

	public DuckDbSetBasedMigrationSnapshotExecutor(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected String createStageSql(final String stage, final String target, final List<String> columns) {
		return "CREATE TEMPORARY TABLE " + stage + " AS SELECT " + list(columns, "t") + " FROM " + target
				+ " AS t WHERE 1=0";
	}

	@Override
	protected String updateTarget(final String target) {
		return "UPDATE " + target + " AS t";
	}
}
