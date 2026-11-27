/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.aurora.mysql.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.aurora.mysql.AuroraMySQL;
import com.sqlapp.data.db.dialect.mysql.bulk.MySqlBulkInsertProvider;

/** Reuses MySQL execution with Aurora identity. */
public class AuroraMySqlBulkInsertProvider extends MySqlBulkInsertProvider {
	/** Creates the Aurora provider using MySQL execution. */
	public AuroraMySqlBulkInsertProvider() {
	}

	@Override
	public boolean supports(Dialect dialect) {
		return dialect instanceof AuroraMySQL;
	}
}
