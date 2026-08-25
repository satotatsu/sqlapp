/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.bulk;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.informix.DialectHolder;
import com.sqlapp.jdbc.bulk.BulkInsertResolver;

class InformixBulkInsertProviderTest {
	@Test
	void resolvesProvider() {
		assertInstanceOf(InformixBulkInsertExecutor.class,
				BulkInsertResolver.resolve(DialectHolder.defaultDialect));
	}
}
