/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.bulk.JdbcBatchBulkInsertExecutor;

/** Firebird Jaybird JDBC batch insert executor. */
public class FirebirdBulkInsertExecutor extends JdbcBatchBulkInsertExecutor {

	public FirebirdBulkInsertExecutor(final Dialect dialect) {
		super(dialect);
	}
}
