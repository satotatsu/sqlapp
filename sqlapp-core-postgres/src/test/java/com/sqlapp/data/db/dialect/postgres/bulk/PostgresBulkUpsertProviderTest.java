/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.bulk;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.jdbc.bulk.BulkUpsertResolver;

class PostgresBulkUpsertProviderTest {
	@Test
	void resolvesOnlyForOnConflictVersions() {
		assertInstanceOf(PostgresBulkUpsertExecutor.class,
				BulkUpsertResolver.resolve(DialectHolder.postgreSQL95));
		assertInstanceOf(PostgresBulkUpsertExecutor.class,
				BulkUpsertResolver.resolve(DialectHolder.postgreSQL180));
		assertThrows(IllegalArgumentException.class,
				() -> BulkUpsertResolver.resolve(DialectHolder.postgreSQL94));
	}
}
