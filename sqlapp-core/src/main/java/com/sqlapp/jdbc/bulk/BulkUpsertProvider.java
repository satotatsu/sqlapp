/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import com.sqlapp.data.db.dialect.Dialect;

/** Service-provider contract for database-specific bulk upserts. */
public interface BulkUpsertProvider {
	boolean supports(Dialect dialect);

	BulkUpsertExecutor create(Dialect dialect);
}
