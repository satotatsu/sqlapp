/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sybase.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.bulk.BulkInsertExecutor;
import com.sqlapp.jdbc.bulk.BulkInsertProvider;

/** Sybase ASE JDBC batch provider. */
public class SybaseBulkInsertProvider implements BulkInsertProvider {
	@Override
	public boolean supports(final Dialect dialect) {
		return dialect != null && "Adaptive Server Enterprise"
				.equalsIgnoreCase(dialect.getProductName());
	}

	@Override
	public BulkInsertExecutor create(final Dialect dialect) {
		return new SybaseBulkInsertExecutor(dialect);
	}
}
