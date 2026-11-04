/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.bulk;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.jdbc.bulk.BulkUpsertResolver;
import com.sqlapp.jdbc.bulk.SetBasedMigrationSnapshotResolver;

class PostgresBulkUpsertProviderTest {
	@Test
	void insertMethodSelectsCopyOrPortableBatchWithoutAConnection() {
		for (var dialect : java.util.List.of(DialectHolder.postgreSQL95, DialectHolder.postgreSQL180)) {
			assertInstanceOf(PostgresBulkInsertExecutor.class, com.sqlapp.jdbc.bulk.BulkInsertResolver.resolve(dialect,
					com.sqlapp.jdbc.bulk.BulkInsertMethod.AUTO));
			assertInstanceOf(PostgresBulkInsertExecutor.class, com.sqlapp.jdbc.bulk.BulkInsertResolver.resolve(dialect,
					com.sqlapp.jdbc.bulk.BulkInsertMethod.NATIVE));
			assertInstanceOf(com.sqlapp.jdbc.bulk.JdbcBatchBulkInsertExecutor.class,
					com.sqlapp.jdbc.bulk.BulkInsertResolver.resolve(dialect,
							com.sqlapp.jdbc.bulk.BulkInsertMethod.JDBC_BATCH));
		}
	}

	@Test
	void resolvesOnlyForOnConflictVersions() {
		assertInstanceOf(PostgresBulkUpsertExecutor.class, BulkUpsertResolver.resolve(DialectHolder.postgreSQL95));
		assertInstanceOf(PostgresBulkUpsertExecutor.class, BulkUpsertResolver.resolve(DialectHolder.postgreSQL180));
		assertThrows(IllegalArgumentException.class, () -> BulkUpsertResolver.resolve(DialectHolder.postgreSQL94));
	}

	@Test
	void resolvesSetBasedSnapshotAcrossSupportedVersions() {
		assertInstanceOf(PostgresSetBasedMigrationSnapshotExecutor.class,
				SetBasedMigrationSnapshotResolver.resolve(DialectHolder.postgreSQL94));
		assertInstanceOf(PostgresSetBasedMigrationSnapshotExecutor.class,
				SetBasedMigrationSnapshotResolver.resolve(DialectHolder.postgreSQL180));
	}
}
