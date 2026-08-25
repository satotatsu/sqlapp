/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.bulk;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.jdbc.bulk.BulkInsertResolver;

class OracleBulkInsertProviderTest {
	@Test
	void resolvesOracleProvider() {
		assertInstanceOf(OracleBulkInsertExecutor.class,
				BulkInsertResolver.resolve(DialectHolder.oracle23aiDialect));
	}
}
