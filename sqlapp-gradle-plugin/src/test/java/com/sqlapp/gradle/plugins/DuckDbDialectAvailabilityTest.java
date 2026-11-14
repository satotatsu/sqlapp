/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.duckdb.DuckDb;

/**
 * The plugin bundles the DuckDB dialect so that tasks connected to a DuckDB
 * JDBC URL resolve it without extra build configuration (the JDBC driver is
 * still supplied by the build).
 */
class DuckDbDialectAvailabilityTest {

	@Test
	void resolvesDuckDbDialectsFromTheProductName() {
		final DuckDb old = assertInstanceOf(DuckDb.class, DialectResolver.getInstance().getDialect("DuckDB", 1, 0, 0));
		final DuckDb current = assertInstanceOf(DuckDb.class,
				DialectResolver.getInstance().getDialect("DuckDB", 2, 0, 0));
		assertTrue(!old.supportsMerge() && current.supportsMerge());
	}
}
