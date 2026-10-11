/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.aurora.postgres;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;
import org.junit.jupiter.api.BeforeAll;

/**
 * Explicitly authorized, disposable Aurora test database only. Never run in the
 * Docker matrix.
 */
class AuroraExternalTest extends AuroraPostgresAssertions {
	@BeforeAll
	static void requireOptIn() throws Exception {
		if (!"true".equals(System.getenv("SQLAPP_AURORA_ALLOW_DESTRUCTIVE_TESTS"))) {
			throw new IllegalStateException(
					"Set SQLAPP_AURORA_ALLOW_DESTRUCTIVE_TESTS=true only for an explicitly authorized disposable Aurora database.");
		}
		for (String name : new String[] { "SQLAPP_AURORA_JDBC_URL", "SQLAPP_AURORA_USER", "SQLAPP_AURORA_PASSWORD" }) {
			if (System.getenv(name) == null || System.getenv(name).isBlank()) {
				throw new IllegalStateException("Required environment variable: " + name);
			}
		}
		if (!System.getenv("SQLAPP_AURORA_JDBC_URL").startsWith("jdbc:postgresql://")) {
			throw new IllegalStateException("SQLAPP_AURORA_JDBC_URL must use jdbc:postgresql://");
		}
		// Validate identity before any test can create a schema or write rows.
		try (var connection = openConnection()) {
			var dialect = com.sqlapp.data.db.dialect.DialectResolver.getInstance().getDialect(connection);
			if (!(dialect instanceof com.sqlapp.data.db.dialect.aurora.postgres.AuroraPostgreSQL)) {
				throw new IllegalStateException("The explicitly selected target is not Aurora PostgreSQL.");
			}
		}
	}

	@Override
	Connection connect() throws Exception {
		return openConnection();
	}

	private static Connection openConnection() throws Exception {
		Properties properties = new Properties();
		properties.setProperty("user", System.getenv("SQLAPP_AURORA_USER"));
		properties.setProperty("password", System.getenv("SQLAPP_AURORA_PASSWORD"));
		properties.setProperty("sslmode", "verify-full");
		return DriverManager.getConnection(System.getenv("SQLAPP_AURORA_JDBC_URL"), properties);
	}

	@Override
	boolean realAurora() {
		return true;
	}
}
