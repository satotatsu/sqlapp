/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.virtica.bulk;

import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.bulk.AbstractStagingMigrationSnapshotExecutor;

/** Vertica local-temporary-table SCD2 flow. */
public final class VirticaSetBasedMigrationSnapshotExecutor extends AbstractStagingMigrationSnapshotExecutor {
	public VirticaSetBasedMigrationSnapshotExecutor(final Dialect dialect) {
		super(dialect);
	}

	@Override
	public boolean supportsCallerTransactionAtomicity() {
		return false;
	}

	@Override
	protected String createStageSql(final String stage, final String target, final List<String> columns) {
		return "CREATE LOCAL TEMPORARY TABLE " + stage + " ON COMMIT PRESERVE ROWS AS SELECT " + list(columns, "t")
				+ " FROM " + target + " t WHERE 1=0";
	}

	@Override
	protected String updateTarget(final String target) {
		return "UPDATE " + target + " AS t";
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
