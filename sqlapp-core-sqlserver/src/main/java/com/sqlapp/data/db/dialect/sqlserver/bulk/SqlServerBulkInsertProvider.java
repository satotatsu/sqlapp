/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.bulk.BulkInsertExecutor;
import com.sqlapp.jdbc.bulk.BulkInsertProvider;

/** SQL Server bulk insert provider. */
public class SqlServerBulkInsertProvider implements BulkInsertProvider {
	@Override
	public boolean supports(final Dialect dialect) {
		return dialect != null && "Microsoft SQL Server".equalsIgnoreCase(dialect.getProductName());
	}

	@Override
	public BulkInsertExecutor create(final Dialect dialect) {
		return new SqlServerBulkInsertExecutor(dialect);
	}
}
