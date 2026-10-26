/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.bulk.JdbcBatchBulkInsertExecutor;

/** SQLite JDBC batch insert executor. */
public class SqliteBulkInsertExecutor extends JdbcBatchBulkInsertExecutor {
	public SqliteBulkInsertExecutor(final Dialect dialect) {
		super(dialect);
	}
}
