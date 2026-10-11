/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import com.sqlapp.data.db.dialect.Dialect;

/** Service-provider extension point for database-specific bulk inserts. */
public interface BulkInsertProvider {
	/** Returns whether this provider supports the resolved dialect. */
	boolean supports(Dialect dialect);

	/** Creates an executor for the supported dialect. */
	BulkInsertExecutor create(Dialect dialect);
}
