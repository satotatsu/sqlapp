/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.bulk.BulkInsertExecutor;
import com.sqlapp.jdbc.bulk.BulkInsertProvider;

/** DB2 JDBC batch provider. */
public class Db2BulkInsertProvider implements BulkInsertProvider {
	@Override
	public boolean supports(final Dialect dialect) {
		return dialect != null && "DB2".equalsIgnoreCase(dialect.getProductName());
	}

	@Override
	public BulkInsertExecutor create(final Dialect dialect) {
		return new Db2BulkInsertExecutor(dialect);
	}
}
