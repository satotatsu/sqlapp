/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.bulk;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.jdbc.bulk.BulkInsertResolver;

class Db2BulkInsertProviderTest {
	@Test
	void resolvesDb2Provider() {
		assertInstanceOf(Db2BulkInsertExecutor.class,
				BulkInsertResolver.resolve(DialectHolder.Db2_1215Dialect));
	}
}
