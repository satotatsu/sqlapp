/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.bulk.BulkUpsertExecutor;
import com.sqlapp.jdbc.bulk.BulkUpsertProvider;

/** Oracle global-temporary-table bulk upsert provider. */
public class OracleBulkUpsertProvider implements BulkUpsertProvider {
	@Override
	public boolean supports(final Dialect dialect) {
		return dialect != null && "Oracle".equalsIgnoreCase(dialect.getProductName())
				&& dialect.supportsMerge();
	}

	@Override
	public BulkUpsertExecutor create(final Dialect dialect) {
		return new OracleBulkUpsertExecutor(dialect);
	}
}
