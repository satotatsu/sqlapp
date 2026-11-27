/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.duckdb.DuckDb;

/**
 * Checks the DuckDB engine that is actually on the test classpath. The
 * per-version Gradle tasks ({@code duckdbCompatibilityTest}) set
 * {@code sqlapp.test.duckdb.expectedVersion} so that a task cannot silently run
 * against a different driver.
 */
class DuckDbVersionCompatibilityTest {

	private static final String EXPECTED_VERSION = "sqlapp.test.duckdb.expectedVersion";

	@Test
	void runsAgainstTheExpectedEngine() throws Exception {
		final String expected = System.getProperty(EXPECTED_VERSION);
		assumeTrue(expected != null && !expected.isBlank(), EXPECTED_VERSION + " is not set");
		try (Connection connection = DriverManager.getConnection("jdbc:duckdb:")) {
			final String actual = connection.getMetaData().getDatabaseProductVersion();
			assertEquals(expected, actual.startsWith("v") ? actual.substring(1) : actual);
		}
	}

	@Test
	void constraintMetadataFailsClearlyBeforeDuckDb11() throws Exception {
		try (Connection connection = DriverManager.getConnection("jdbc:duckdb:");
				Statement statement = connection.createStatement()) {
			final DuckDb dialect = assertInstanceOf(DuckDb.class, DialectResolver.getInstance().getDialect(connection));
			assumeFalse(dialect.supportsConstraintMetadata(), "DuckDB 1.1 or later");
			statement.execute("CREATE TABLE t (id INTEGER PRIMARY KEY)");
			final var reader = dialect.getCatalogReader().getSchemaReader();
			reader.setSchemaName("main");
			assertThrows(UnsupportedOperationException.class, () -> reader.getAllFull(connection));
		}
	}
}
