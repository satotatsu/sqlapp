/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.bulk;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.sqlserver.DialectHolder;
import com.sqlapp.jdbc.bulk.BulkUpsertResolver;

class SqlServerBulkUpsertProviderTest {
	@Test
	void resolvesProvider() {
		assertInstanceOf(SqlServerBulkUpsertExecutor.class,
				BulkUpsertResolver.resolve(DialectHolder.defaultDialect2022));
		assertThrows(IllegalArgumentException.class,
				() -> BulkUpsertResolver.resolve(DialectHolder.defaultDialect2005));
	}
}
