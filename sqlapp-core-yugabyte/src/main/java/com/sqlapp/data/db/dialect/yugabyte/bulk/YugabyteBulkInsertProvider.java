/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.yugabyte.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.yugabyte.Yugabyte11;
import com.sqlapp.data.db.dialect.yugabyte.Yugabyte15;
import com.sqlapp.jdbc.bulk.BulkInsertExecutor;
import com.sqlapp.jdbc.bulk.BulkInsertProvider;

/** YSQL provider using PostgreSQL COPY with an atomic YSQL transaction. */
public class YugabyteBulkInsertProvider implements BulkInsertProvider {
	@Override
	public boolean supports(Dialect dialect) {
		return dialect instanceof Yugabyte11 || dialect instanceof Yugabyte15;
	}

	@Override
	public BulkInsertExecutor create(Dialect dialect) {
		return new YugabyteBulkInsertExecutor(dialect);
	}
}
