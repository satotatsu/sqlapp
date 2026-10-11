/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.bulk.JdbcBatchBulkInsertExecutor;

/** SAP HANA JDBC batch insert executor. */
public class SapHanaBulkInsertExecutor extends JdbcBatchBulkInsertExecutor {
	public SapHanaBulkInsertExecutor(final Dialect dialect) {
		super(dialect);
	}
}
