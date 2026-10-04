/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.bulk.BulkUpsertExecutor;
import com.sqlapp.jdbc.bulk.BulkUpsertProvider;

/** HSQLDB 2.x MERGE provider. */
public class HsqlBulkUpsertProvider implements BulkUpsertProvider {
	@Override
	public boolean supports(final Dialect dialect) {
		return dialect != null && "HSQL".equalsIgnoreCase(dialect.getProductName());
	}

	@Override
	public BulkUpsertExecutor create(final Dialect dialect) {
		return new HsqlBulkUpsertExecutor(dialect);
	}
}
