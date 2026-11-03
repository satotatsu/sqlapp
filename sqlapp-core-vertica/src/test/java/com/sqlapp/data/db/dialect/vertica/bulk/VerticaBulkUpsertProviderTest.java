/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.vertica.bulk;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.vertica.DialectHolder;
import com.sqlapp.data.db.dialect.vertica.bulk.VerticaBulkUpsertExecutor;
import com.sqlapp.data.db.dialect.vertica.bulk.VerticaSetBasedMigrationSnapshotExecutor;
import com.sqlapp.jdbc.bulk.BulkUpsertResolver;
import com.sqlapp.jdbc.bulk.SetBasedMigrationSnapshotResolver;

class VerticaBulkUpsertProviderTest {
	@Test
	void resolvesProvider() {
		assertInstanceOf(VerticaBulkUpsertExecutor.class,
				BulkUpsertResolver.resolve(DialectHolder.defaultDialect12_0_4));
	}

	@Test
	void resolvesSetBasedSnapshotProvider() {
		final var executor = SetBasedMigrationSnapshotResolver.resolve(DialectHolder.defaultDialect12_0_4);
		assertInstanceOf(VerticaSetBasedMigrationSnapshotExecutor.class, executor);
		assertFalse(executor.supportsCallerTransactionAtomicity());
	}
}
