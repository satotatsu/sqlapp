/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.bulk;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.mysql.DialectHolder;
import com.sqlapp.jdbc.bulk.BulkUpsertResolver;

class MySqlBulkUpsertProviderTest {
	@Test void resolvesProvider() {
		assertInstanceOf(MySqlBulkUpsertExecutor.class,
				BulkUpsertResolver.resolve(DialectHolder.mysql840Dialect));
	}
}
