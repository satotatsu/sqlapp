/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.resolver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.DatabaseMetaData;
import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.duckdb.DialectHolder;
import com.sqlapp.data.db.dialect.duckdb.DuckDb;
import com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver;

class DuckDbDialectResolverTest {

	@Test
	void resolvesVersionBoundaries() {
		final DialectResolver resolver = DialectResolver.getInstance();
		assertSame(DialectHolder.duckdb10, resolver.getDialect("DuckDB", 0, 10, 3));
		assertSame(DialectHolder.duckdb10, resolver.getDialect("DuckDB", 1, 0, 0));
		assertSame(DialectHolder.duckdb11, resolver.getDialect("DuckDB", 1, 1, 3));
		assertSame(DialectHolder.duckdb12, resolver.getDialect("DuckDB", 1, 2, 2));
		assertSame(DialectHolder.duckdb12, resolver.getDialect("DuckDB", 1, 3, 2));
		assertSame(DialectHolder.duckdb14, resolver.getDialect("DuckDB", 1, 4, 4));
		assertSame(DialectHolder.duckdb14, resolver.getDialect("DuckDB", 1, 5, 0));
		assertSame(DialectHolder.duckdb14, resolver.getDialect("DuckDB", 2, 0, 0));
	}

	@Test
	void versionCapabilities() {
		assertFalse(((DuckDb) DialectHolder.duckdb10).supportsConstraintMetadata());
		assertTrue(((DuckDb) DialectHolder.duckdb11).supportsConstraintMetadata());
		assertFalse(((DuckDb) DialectHolder.duckdb11).supportsAlterTableAddPrimaryKey());
		assertTrue(((DuckDb) DialectHolder.duckdb12).supportsAlterTableAddPrimaryKey());
		assertFalse(DialectHolder.duckdb12.supportsMerge());
		assertTrue(DialectHolder.duckdb14.supportsMerge());
	}

	@Test
	void parsesProductVersionFromMetadata() {
		final DuckDbDialectResolver resolver = new DuckDbDialectResolver();
		assertSame(DialectHolder.duckdb11, resolver.resolveDatabaseMetaData(metadata("DuckDB", "v1.1.3")));
		assertSame(DialectHolder.duckdb14, resolver.resolveDatabaseMetaData(metadata("DuckDB", "1.4.4")));
		assertSame(DialectHolder.duckdb14, resolver.resolveDatabaseMetaData(metadata("DuckDB", "v2.0.0")));
		assertNull(resolver.resolveDatabaseMetaData(metadata("H2", "2.3.232")));
		assertNull(resolver.resolveDatabaseMetaData(metadata("DuckDB", "unknown")));
	}

	@Test
	void serviceLoaderFindsResolver() {
		boolean found = false;
		for (final ProductNameDialectResolver resolver : ServiceLoader.load(ProductNameDialectResolver.class)) {
			found |= resolver instanceof DuckDbDialectResolver;
		}
		assertTrue(found);
	}

	private static DatabaseMetaData metadata(final String productName, final String productVersion) {
		return (DatabaseMetaData) Proxy.newProxyInstance(DuckDbDialectResolverTest.class.getClassLoader(),
				new Class<?>[] { DatabaseMetaData.class }, (proxy, method, args) -> switch (method.getName()) {
				case "getDatabaseProductName" -> productName;
				case "getDatabaseProductVersion" -> productVersion;
				default -> throw new UnsupportedOperationException(method.getName());
				});
	}
}
