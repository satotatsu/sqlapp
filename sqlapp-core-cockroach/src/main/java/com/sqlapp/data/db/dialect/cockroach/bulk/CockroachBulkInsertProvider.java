/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.cockroach.CockroachDB;
import com.sqlapp.jdbc.bulk.*;

public class CockroachBulkInsertProvider implements BulkInsertProvider {
	@Override
	public boolean supports(Dialect dialect) {
		return dialect instanceof CockroachDB;
	}

	@Override
	public BulkInsertExecutor create(Dialect dialect) {
		return new CockroachBulkInsertExecutor(dialect);
	}
}
