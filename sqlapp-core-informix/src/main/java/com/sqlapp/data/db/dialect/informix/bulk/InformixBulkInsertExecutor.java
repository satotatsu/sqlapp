/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.bulk.JdbcBatchBulkInsertExecutor;

/** Informix JDBC batch insert executor. */
public class InformixBulkInsertExecutor extends JdbcBatchBulkInsertExecutor {

	public InformixBulkInsertExecutor(final Dialect dialect) {
		super(dialect);
	}
}
