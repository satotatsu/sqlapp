/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.bulk.JdbcBatchBulkInsertExecutor;

/** Oracle JDBC batch insert executor. */
public class OracleBulkInsertExecutor extends JdbcBatchBulkInsertExecutor {
	public OracleBulkInsertExecutor(final Dialect dialect) {
		super(dialect);
	}
}
