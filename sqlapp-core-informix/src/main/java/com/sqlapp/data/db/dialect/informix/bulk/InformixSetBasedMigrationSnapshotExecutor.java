/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.bulk;

import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.bulk.AbstractStagingMigrationSnapshotExecutor;

/** Informix session-temporary-table SCD2 flow. */
public final class InformixSetBasedMigrationSnapshotExecutor extends AbstractStagingMigrationSnapshotExecutor {
	public InformixSetBasedMigrationSnapshotExecutor(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected String createStageSql(final String stage, final String target, final List<String> columns) {
		return "SELECT " + list(columns, "t") + " FROM " + target + " t WHERE 1=0 INTO TEMP " + stage + " WITH NO LOG";
	}

	@Override
	protected String updateTarget(final String target) {
		return "UPDATE " + target + " t";
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
