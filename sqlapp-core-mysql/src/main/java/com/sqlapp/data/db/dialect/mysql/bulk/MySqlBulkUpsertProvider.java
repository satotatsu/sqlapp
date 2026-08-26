/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.bulk.BulkUpsertExecutor;
import com.sqlapp.jdbc.bulk.BulkUpsertProvider;

/** MySQL temporary-table upsert provider. */
public class MySqlBulkUpsertProvider implements BulkUpsertProvider {
	@Override public boolean supports(final Dialect dialect) {
		return dialect != null && "MySQL".equalsIgnoreCase(dialect.getProductName());
	}
	@Override public BulkUpsertExecutor create(final Dialect dialect) {
		return new MySqlBulkUpsertExecutor(dialect);
	}
}
