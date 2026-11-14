/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.sql;

import com.sqlapp.data.db.dialect.duckdb.util.DuckDbSqlBuilder;
import com.sqlapp.data.db.sql.AbstractTruncateTemporaryTableFactory;
import com.sqlapp.data.schemas.Table;

/**
 * {@code TRUNCATE TABLE} for a DuckDB temporary table (never schema qualified).
 */
public class DuckDbTruncateTemporaryTableFactory extends AbstractTruncateTemporaryTableFactory<DuckDbSqlBuilder> {

	@Override
	protected void addTruncateTable(final Table obj, final DuckDbSqlBuilder builder) {
		final String prefix = getTableOptions().getTemporaryTableNamePrefix().apply(obj);
		final String suffix = getTableOptions().getTemporaryTableNameSuffix().apply(obj);
		builder.truncate().table().space()._add(getDialect().getTemporaryTableName(obj, prefix, suffix, false));
	}
}
