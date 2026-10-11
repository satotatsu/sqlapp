/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.bulk.BulkUpsertExecutor;
import com.sqlapp.jdbc.bulk.BulkUpsertProvider;

/** SQLite 3.24+ ON CONFLICT provider. */
public class SqliteBulkUpsertProvider implements BulkUpsertProvider {
	@Override
	public boolean supports(final Dialect d) {
		return d != null && "SQLite".equalsIgnoreCase(d.getProductName());
	}

	@Override
	public BulkUpsertExecutor create(final Dialect d) {
		return new SqliteBulkUpsertExecutor(d);
	}

}
