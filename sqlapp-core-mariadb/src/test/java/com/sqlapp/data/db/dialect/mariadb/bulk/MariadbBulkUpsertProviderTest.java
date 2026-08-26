/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mariadb.bulk;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.mariadb.DialectHolder;
import com.sqlapp.data.db.dialect.mysql.bulk.MySqlBulkUpsertExecutor;
import com.sqlapp.jdbc.bulk.BulkUpsertResolver;

class MariadbBulkUpsertProviderTest {
	@Test void resolvesProvider() {
		assertInstanceOf(MySqlBulkUpsertExecutor.class,
				BulkUpsertResolver.resolve(DialectHolder.mariadb11_80Dialect));
	}
}
