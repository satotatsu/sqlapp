/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sybase.bulk;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.sybase.DialectHolder;
import com.sqlapp.jdbc.bulk.BulkInsertResolver;

class SybaseBulkInsertProviderTest {
	@Test
	void resolvesSybaseProvider() {
		assertInstanceOf(SybaseBulkInsertExecutor.class,
				BulkInsertResolver.resolve(DialectHolder.defaultDialect));
	}
}
