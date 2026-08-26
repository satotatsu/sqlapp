/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.bulk;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.jdbc.bulk.BulkUpsertResolver;

class OracleBulkUpsertProviderTest {
	@Test
	void resolvesProvider() {
		assertInstanceOf(OracleBulkUpsertExecutor.class,
				BulkUpsertResolver.resolve(DialectHolder.oracle23aiDialect));
	}
}
