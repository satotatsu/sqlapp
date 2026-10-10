/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.alloydb;

import java.sql.Connection;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Runs the official AlloyDB Omni engine, not a PostgreSQL substitute. */
class AlloyDBOmniCompatibilityTest extends AlloyDBAssertions {
	private static final PostgreSQLContainer OMNI = new PostgreSQLContainer(
			DockerImageName.parse(System.getProperty("sqlapp.test.alloydb.image", "google/alloydbomni:16.8.0"))
					.asCompatibleSubstituteFor("postgres"))
			.withSharedMemorySize(256L * 1024 * 1024).withStartupTimeout(Duration.ofMinutes(3));

	@BeforeAll
	static void start() throws Exception {
		OMNI.start();
		try (var connection = OMNI.createConnection("")) {
			int expected = Integer.parseInt(System.getProperty("sqlapp.test.alloydb.expectedEngineMajor", "16"));
			org.junit.jupiter.api.Assertions.assertEquals(expected, connection.getMetaData().getDatabaseMajorVersion());
		}
	}

	@AfterAll
	static void stop() {
		OMNI.stop();
	}

	@Override
	Connection connect() throws Exception {
		return OMNI.createConnection("");
	}

	@Override
	boolean alloyDbTarget() {
		return true;
	}

	@org.junit.jupiter.api.Test
	void identifiesWithColumnarDisabledAndWithoutSuperuser() throws Exception {
		String role = "sqlapp_role_" + java.util.UUID.randomUUID().toString().replace("-", "");
		String password = java.util.UUID.randomUUID().toString();
		try (var admin = connect(); var statement = admin.createStatement()) {
			try (var rows = statement.executeQuery(
					"SELECT setting FROM pg_catalog.pg_settings WHERE name = 'google_columnar_engine.enabled'")) {
				org.junit.jupiter.api.Assertions.assertTrue(rows.next());
				org.junit.jupiter.api.Assertions.assertEquals("off", rows.getString(1));
			}
			statement.execute("CREATE ROLE " + role + " LOGIN PASSWORD '" + password + "'");
			try (var connection = java.sql.DriverManager.getConnection(OMNI.getJdbcUrl(), role, password)) {
				connection.setAutoCommit(false);
				org.junit.jupiter.api.Assertions.assertInstanceOf(com.sqlapp.data.db.dialect.alloydb.AlloyDB.class,
						com.sqlapp.data.db.dialect.DialectResolver.getInstance().getDialect(connection));
				try (var sql = connection.createStatement(); var rows = sql.executeQuery("SELECT 42")) {
					org.junit.jupiter.api.Assertions.assertTrue(rows.next());
					org.junit.jupiter.api.Assertions.assertEquals(42, rows.getInt(1));
				}
				connection.rollback();
			} finally {
				statement.execute("DROP ROLE " + role);
			}
		}
	}

}
