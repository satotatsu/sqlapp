/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.derby;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.jdbc.bulk.SetBasedMigrationSnapshotResolver;

class DerbySetBasedMigrationSnapshotResolverTest {
	@Test
	void usesStreamingFallback() {
		assertTrue(SetBasedMigrationSnapshotResolver.find(DialectHolder.defaultDialect).isEmpty());
	}
}
