/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.duckdb.sql;

import java.util.List;

import com.sqlapp.data.db.dialect.duckdb.util.DuckDbSqlBuilder;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.StandardAlterSequenceFactory;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.State;

/**
 * DuckDB cannot alter a sequence (not even RESTART), so a modified sequence
 * fails with the changed properties instead of being silently skipped.
 */
public class DuckDbAlterSequenceFactory extends StandardAlterSequenceFactory<DuckDbSqlBuilder> {
	@Override
	public boolean supportsRestart() {
		return false;
	}

	@Override
	public List<SqlOperation> createDiffSql(final DbObjectDifference difference) {
		if (difference.getState() == State.Modified) {
			throw new IllegalArgumentException("DuckDB cannot alter sequence "
					+ ((com.sqlapp.data.schemas.Sequence) difference.getTarget()).getName() + " in place "
					+ difference.getChangedProperties(getDialect()).keySet() + "; drop and recreate it explicitly");
		}
		return super.createDiffSql(difference);
	}
}
