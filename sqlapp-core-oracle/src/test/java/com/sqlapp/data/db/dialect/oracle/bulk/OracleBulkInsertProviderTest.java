/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.bulk;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.jdbc.bulk.BulkInsertResolver;
import com.sqlapp.jdbc.bulk.SetBasedMigrationSnapshotResolver;

class OracleBulkInsertProviderTest {
	@Test
	void resolvesOracleProvider() {
		assertInstanceOf(OracleBulkInsertExecutor.class,
				BulkInsertResolver.resolve(DialectHolder.oracle23aiDialect));
	}

	@Test
	void resolvesSetBasedSnapshotProviderFromOracle18c() {
		assertInstanceOf(OracleSetBasedMigrationSnapshotExecutor.class,
				SetBasedMigrationSnapshotResolver.resolve(DialectHolder.oracle18cDialect));
		assertInstanceOf(OracleSetBasedMigrationSnapshotExecutor.class,
				SetBasedMigrationSnapshotResolver.resolve(DialectHolder.oracle23aiDialect));
	}

	@Test
	void keepsStreamingFallbackBeforeOracle18c() {
		assertTrue(SetBasedMigrationSnapshotResolver.find(DialectHolder.oracle12cDialect).isEmpty());
	}
}
