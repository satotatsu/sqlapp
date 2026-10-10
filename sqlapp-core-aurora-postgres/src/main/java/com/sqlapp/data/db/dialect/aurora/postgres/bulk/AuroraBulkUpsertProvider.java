/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.aurora.postgres.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.aurora.postgres.AuroraPostgreSQL;
import com.sqlapp.data.db.dialect.postgres.bulk.PostgresBulkUpsertProvider;

/** Reuses PostgreSQL execution with Aurora product identity. */
public class AuroraBulkUpsertProvider extends PostgresBulkUpsertProvider {
	@Override
	public boolean supports(Dialect dialect) {
		return dialect instanceof AuroraPostgreSQL;
	}
}
