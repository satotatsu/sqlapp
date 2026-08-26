/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.Postgres95;
import com.sqlapp.jdbc.bulk.BulkUpsertExecutor;
import com.sqlapp.jdbc.bulk.BulkUpsertProvider;

/** PostgreSQL 9.5+ staging-table bulk upsert provider. */
public class PostgresBulkUpsertProvider implements BulkUpsertProvider {
	@Override
	public boolean supports(final Dialect dialect) {
		return dialect instanceof Postgres95;
	}

	@Override
	public BulkUpsertExecutor create(final Dialect dialect) {
		return new PostgresBulkUpsertExecutor(dialect);
	}
}
