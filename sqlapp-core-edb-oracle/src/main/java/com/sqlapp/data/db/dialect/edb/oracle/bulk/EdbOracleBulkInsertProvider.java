/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.edb.oracle.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.edb.oracle.EdbOracle;
import com.sqlapp.data.db.dialect.postgres.bulk.PostgresBulkInsertProvider;

/** Reuses PostgreSQL execution for the Advanced Server baseline. */
public class EdbOracleBulkInsertProvider extends PostgresBulkInsertProvider {
	@Override public boolean supports(Dialect dialect) { return dialect instanceof EdbOracle; }
}
