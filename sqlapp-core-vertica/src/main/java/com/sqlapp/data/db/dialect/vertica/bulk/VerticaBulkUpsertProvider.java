/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.vertica.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.bulk.BulkUpsertExecutor;
import com.sqlapp.jdbc.bulk.BulkUpsertProvider;

/** Vertica COPY and MERGE provider. */
public class VerticaBulkUpsertProvider implements BulkUpsertProvider {
	@Override
	public boolean supports(final Dialect d) {
		return d != null && "Vertica".equalsIgnoreCase(d.getProductName()) && d.supportsMerge();
	}

	@Override
	public BulkUpsertExecutor create(final Dialect d) {
		return new VerticaBulkUpsertExecutor(d);
	}
}
