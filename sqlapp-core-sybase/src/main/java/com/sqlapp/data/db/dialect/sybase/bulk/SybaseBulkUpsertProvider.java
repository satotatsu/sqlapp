/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sybase.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.bulk.BulkUpsertExecutor;
import com.sqlapp.jdbc.bulk.BulkUpsertProvider;

/** Sybase ASE local-temporary-table MERGE provider. */
public class SybaseBulkUpsertProvider implements BulkUpsertProvider {
	@Override
	public boolean supports(final Dialect dialect) {
		return dialect != null && "Adaptive Server Enterprise".equalsIgnoreCase(dialect.getProductName());
	}

	@Override
	public BulkUpsertExecutor create(final Dialect dialect) {
		return new SybaseBulkUpsertExecutor(dialect);
	}
}
