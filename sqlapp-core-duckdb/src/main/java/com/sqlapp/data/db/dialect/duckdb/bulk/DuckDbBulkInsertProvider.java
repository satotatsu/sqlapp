/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.duckdb.DuckDb;
import com.sqlapp.jdbc.bulk.BulkInsertExecutor;
import com.sqlapp.jdbc.bulk.BulkInsertProvider;

/**
 * DuckDB bulk INSERT provider: Appender with a type-aware JDBC batch fallback.
 */
public class DuckDbBulkInsertProvider implements BulkInsertProvider {
	@Override
	public boolean supports(final Dialect dialect) {
		return dialect instanceof DuckDb;
	}

	@Override
	public BulkInsertExecutor create(final Dialect dialect) {
		return new DuckDbBulkInsertExecutor(dialect);
	}
}
