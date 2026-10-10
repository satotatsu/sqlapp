/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.cockroach.CockroachDB;
import com.sqlapp.jdbc.bulk.*;

public class CockroachBulkUpsertProvider implements BulkUpsertProvider {
	@Override
	public boolean supports(Dialect dialect) {
		return dialect instanceof CockroachDB;
	}

	@Override
	public BulkUpsertExecutor create(Dialect dialect) {
		return new CockroachBulkUpsertExecutor(dialect);
	}
}
