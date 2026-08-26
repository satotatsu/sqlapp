/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.bulk.BulkUpsertExecutor;
import com.sqlapp.jdbc.bulk.BulkUpsertProvider;

/** Firebird UPDATE OR INSERT provider. */
public class FirebirdBulkUpsertProvider implements BulkUpsertProvider {
	@Override
	public boolean supports(final Dialect d) {
		return d != null && "Firebird".equalsIgnoreCase(d.getProductName());
	}

	@Override
	public BulkUpsertExecutor create(final Dialect d) {
		return new FirebirdBulkUpsertExecutor(d);
	}

}
