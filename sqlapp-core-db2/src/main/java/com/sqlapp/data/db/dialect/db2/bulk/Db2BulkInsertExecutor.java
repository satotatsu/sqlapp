/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.bulk.JdbcBatchBulkInsertExecutor;

/** DB2 JCC optimized JDBC batch insert executor. */
public class Db2BulkInsertExecutor extends JdbcBatchBulkInsertExecutor {
	public Db2BulkInsertExecutor(final Dialect dialect) {
		super(dialect);
	}
}
