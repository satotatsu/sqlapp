/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.bulk;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.jdbc.bulk.SetBasedMigrationSnapshotResolver;

class FirebirdSetBasedMigrationSnapshotResolverTest {
	@Test
	void usesStreamingFallbackAcrossSupportedVersions() {
		assertTrue(SetBasedMigrationSnapshotResolver.find(DialectHolder.defaultDialect).isEmpty());
		assertTrue(SetBasedMigrationSnapshotResolver.find(DialectHolder.defaultDialect25).isEmpty());
		assertTrue(SetBasedMigrationSnapshotResolver.find(DialectHolder.defaultDialect50).isEmpty());
	}
}
