/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.sqlapp.jdbc.bulk.SetBasedMigrationSnapshotResolver;

class PhoenixSetBasedMigrationSnapshotResolverTest {
	@Test void usesStreamingFallbackAcrossSupportedVersions() {
		assertTrue(SetBasedMigrationSnapshotResolver.find(DialectHolder.defaultDialect).isEmpty());
		assertTrue(SetBasedMigrationSnapshotResolver.find(DialectHolder.defaultDialect5_3_1).isEmpty());
	}
}
