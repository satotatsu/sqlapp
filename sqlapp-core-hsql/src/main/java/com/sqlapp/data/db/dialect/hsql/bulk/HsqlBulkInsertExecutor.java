/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.bulk.JdbcBatchBulkInsertExecutor;

/** HSQLDB JDBC batch insert executor. */
public class HsqlBulkInsertExecutor extends JdbcBatchBulkInsertExecutor {
	public HsqlBulkInsertExecutor(final Dialect dialect) {
		super(dialect);
	}
}
