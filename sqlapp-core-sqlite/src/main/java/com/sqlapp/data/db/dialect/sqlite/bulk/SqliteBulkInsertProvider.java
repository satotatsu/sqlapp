/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.bulk.BulkInsertExecutor;
import com.sqlapp.jdbc.bulk.BulkInsertProvider;

/** SQLite JDBC batch provider. */
public class SqliteBulkInsertProvider implements BulkInsertProvider {
	@Override
	public boolean supports(final Dialect dialect) {
		return dialect != null && "SQLite".equalsIgnoreCase(dialect.getProductName());
	}

	@Override
	public BulkInsertExecutor create(final Dialect dialect) {
		return new SqliteBulkInsertExecutor(dialect);
	}
}
