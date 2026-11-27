/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.model;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.duckdb.DuckDb;
import com.sqlapp.jdbc.bulk.MigrationModelExecutor;
import com.sqlapp.jdbc.bulk.MigrationModelExecutorProvider;

/** DuckDB model executor provider. */
public class DuckDbMigrationModelExecutorProvider implements MigrationModelExecutorProvider {
	@Override
	public boolean supports(final Dialect dialect) {
		return dialect instanceof DuckDb;
	}

	@Override
	public MigrationModelExecutor create(final Dialect dialect) {
		return new DuckDbMigrationModelExecutor(dialect);
	}
}
