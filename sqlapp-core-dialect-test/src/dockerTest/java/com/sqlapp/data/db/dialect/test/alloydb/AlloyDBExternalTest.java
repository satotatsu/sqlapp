/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.alloydb;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;
import org.junit.jupiter.api.BeforeAll;

/**
 * Explicitly authorized, disposable AlloyDB test database only. Never run in
 * the Docker matrix.
 */
class AlloyDBExternalTest extends AlloyDBAssertions {
	@BeforeAll
	static void requireOptIn() throws Exception {
		if (!"true".equals(System.getenv("SQLAPP_ALLOYDB_ALLOW_DESTRUCTIVE_TESTS"))) {
			throw new IllegalStateException(
					"Set SQLAPP_ALLOYDB_ALLOW_DESTRUCTIVE_TESTS=true only for an explicitly authorized disposable AlloyDB database.");
		}
		for (String name : new String[] { "SQLAPP_ALLOYDB_JDBC_URL", "SQLAPP_ALLOYDB_USER",
				"SQLAPP_ALLOYDB_PASSWORD" }) {
			if (System.getenv(name) == null || System.getenv(name).isBlank()) {
				throw new IllegalStateException("Required environment variable: " + name);
			}
		}
		if (!System.getenv("SQLAPP_ALLOYDB_JDBC_URL").startsWith("jdbc:postgresql://")) {
			throw new IllegalStateException("SQLAPP_ALLOYDB_JDBC_URL must use jdbc:postgresql://");
		}
		// Validate identity before any test can create a schema or write rows.
		try (var connection = openConnection()) {
			var dialect = com.sqlapp.data.db.dialect.DialectResolver.getInstance().getDialect(connection);
			if (!(dialect instanceof com.sqlapp.data.db.dialect.alloydb.AlloyDB)) {
				throw new IllegalStateException("The explicitly selected target is not AlloyDB.");
			}
		}
	}

	@Override
	Connection connect() throws Exception {
		return openConnection();
	}

	private static Connection openConnection() throws Exception {
		Properties properties = new Properties();
		properties.setProperty("user", System.getenv("SQLAPP_ALLOYDB_USER"));
		properties.setProperty("password", System.getenv("SQLAPP_ALLOYDB_PASSWORD"));
		properties.setProperty("sslmode", "verify-full");
		return DriverManager.getConnection(System.getenv("SQLAPP_ALLOYDB_JDBC_URL"), properties);
	}

	@Override
	boolean alloyDbTarget() {
		return true;
	}
}
