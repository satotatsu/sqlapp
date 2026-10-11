/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.bulk;

import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.bulk.AbstractStagingMigrationSnapshotExecutor;

/** SQL Server-specific local-temp-table and UPDATE FROM SCD2 flow. */
public final class SqlServerSetBasedMigrationSnapshotExecutor extends AbstractStagingMigrationSnapshotExecutor {
	public SqlServerSetBasedMigrationSnapshotExecutor(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected String stageIdentifier(final String name) {
		return dialect.quote("#" + name);
	}

	@Override
	protected String createStageSql(final String stage, final String target, final List<String> columns) {
		return "SELECT " + list(columns, "t") + " INTO " + stage + " FROM " + target + " t WHERE 1=0";
	}

	@Override
	protected String updateTarget(final String target) {
		return "UPDATE t";
	}

	@Override
	protected String updateFrom(final String target) {
		return " FROM " + target + " t";
	}

	@Override
	protected String cleanupStageSql(final String stage) {
		return "DROP TABLE " + stage;
	}

	@Override
	protected String valuesEqual(final String left, final String right) {
		return "(" + left + " = " + right + " OR (" + left + " IS NULL AND " + right + " IS NULL))";
	}

	@Override
	protected String valuesDifferent(final String left, final String right) {
		return "(" + left + " <> " + right + " OR (" + left + " IS NULL AND " + right + " IS NOT NULL) OR (" + left
				+ " IS NOT NULL AND " + right + " IS NULL))";
	}
}
