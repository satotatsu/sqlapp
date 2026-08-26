/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.bulk;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.jdbc.bulk.BulkUpsertExecutor;
import com.sqlapp.jdbc.bulk.BulkUpsertProvider;

/** SAP HANA local-table MERGE provider. */
public class SapHanaBulkUpsertProvider implements BulkUpsertProvider {
	@Override public boolean supports(final Dialect dialect) {
		return dialect != null && "SAP HANA".equalsIgnoreCase(dialect.getProductName())
				&& dialect.supportsMerge();
	}
	@Override public BulkUpsertExecutor create(final Dialect dialect) {
		return new SapHanaBulkUpsertExecutor(dialect);
	}
}
