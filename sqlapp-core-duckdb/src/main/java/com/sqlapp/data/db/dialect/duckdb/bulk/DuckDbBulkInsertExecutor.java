/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.bulk.JdbcBatchBulkInsertExecutor;

/**
 * JDBC batch INSERT for DuckDB. Values are bound with the column type handlers
 * registered by the DuckDB dialect (see
 * {@link com.sqlapp.data.db.dialect.duckdb.util.DuckDbJdbcTypeHandlers}); the
 * DuckDB JDBC driver rejects or corrupts several types when they are bound by
 * Java class.
 */
public class DuckDbBulkInsertExecutor extends JdbcBatchBulkInsertExecutor {

	public DuckDbBulkInsertExecutor(final Dialect dialect) {
		super(dialect);
	}
}
