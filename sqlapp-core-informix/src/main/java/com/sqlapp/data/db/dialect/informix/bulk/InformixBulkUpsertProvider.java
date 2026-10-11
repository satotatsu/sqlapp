/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.bulk.BulkUpsertExecutor;
import com.sqlapp.jdbc.bulk.BulkUpsertProvider;

/** Informix temporary-table MERGE provider. */
public class InformixBulkUpsertProvider implements BulkUpsertProvider {
	@Override
	public boolean supports(final Dialect dialect) {
		return dialect != null && "Informix Dynamic Server".equalsIgnoreCase(dialect.getProductName());
	}

	@Override
	public BulkUpsertExecutor create(final Dialect dialect) {
		return new InformixBulkUpsertExecutor(dialect);
	}
}
