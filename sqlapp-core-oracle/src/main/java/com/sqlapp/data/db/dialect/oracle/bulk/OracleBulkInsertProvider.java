/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.bulk.BulkInsertExecutor;
import com.sqlapp.jdbc.bulk.BulkInsertProvider;

/** Oracle JDBC batch provider. */
public class OracleBulkInsertProvider implements BulkInsertProvider {
	@Override
	public boolean supports(final Dialect dialect) {
		return dialect != null && "Oracle".equalsIgnoreCase(dialect.getProductName());
	}

	@Override
	public BulkInsertExecutor create(final Dialect dialect) {
		return new OracleBulkInsertExecutor(dialect);
	}
}
