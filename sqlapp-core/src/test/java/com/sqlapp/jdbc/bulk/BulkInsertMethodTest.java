/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.AbstractDbTest;

class BulkInsertMethodTest extends AbstractDbTest {
	@Test
	void portableMethodAndAutomaticFallbackDoNotRequireAVendorProvider() throws Exception {
		testDb(connection -> {
			var dialect = getDialect(connection);
			assertInstanceOf(JdbcBatchBulkInsertExecutor.class, BulkInsertResolver.resolve(dialect, BulkInsertMethod.JDBC_BATCH));
			assertInstanceOf(JdbcBatchBulkInsertExecutor.class, BulkInsertResolver.resolve(dialect, BulkInsertMethod.AUTO));
			assertThrows(IllegalArgumentException.class, () -> BulkInsertResolver.resolve(dialect, BulkInsertMethod.NATIVE));
			assertThrows(IllegalArgumentException.class, () -> BulkInsertResolver.resolve(dialect));
		});
	}

	@Test
	void omittedMethodRetainsAutomaticDefaultsAndUpsertRejectsUnsupportedExplicitSelection() {
		assertEquals(BulkInsertMethod.AUTO, BulkOption.defaults().getInsertMethod());
		assertEquals(BulkInsertMethod.AUTO, BulkOption.builder().insertMethod(null).build().getInsertMethod());
		assertThrows(IllegalArgumentException.class, () -> ChunkedBulkMigrationOption.builder().migrationId("test")
				.mode(BulkMigrationMode.UPSERT).bulkOption(BulkOption.builder().insertMethod(BulkInsertMethod.JDBC_BATCH).build()).build());
	}
}
