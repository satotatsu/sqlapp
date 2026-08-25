/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.bulk;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.saphana.DialectHolder;
import com.sqlapp.jdbc.bulk.BulkInsertResolver;

class SapHanaBulkInsertProviderTest {
	@Test
	void resolvesSapHanaProvider() {
		assertInstanceOf(SapHanaBulkInsertExecutor.class,
				BulkInsertResolver.resolve(DialectHolder.defaultDialect));
	}
}
