/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.bulk.BulkUpsertExecutor;
import com.sqlapp.jdbc.bulk.BulkUpsertProvider;

/** SQL Server staging-table bulk upsert provider. */
public class SqlServerBulkUpsertProvider implements BulkUpsertProvider {
	@Override
	public boolean supports(final Dialect dialect) {
		return dialect != null && "Microsoft SQL Server"
				.equalsIgnoreCase(dialect.getProductName())
				&& dialect.supportsMerge();
	}

	@Override
	public BulkUpsertExecutor create(final Dialect dialect) {
		return new SqlServerBulkUpsertExecutor(dialect);
	}
}
