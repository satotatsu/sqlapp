/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.aurora;

import java.sql.Connection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** PostgreSQL compatibility only: this container does not emulate Aurora. */
class AuroraPostgresCompatibilityTest extends AuroraPostgresAssertions {
	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
			System.getProperty("sqlapp.test.aurora.postgresImage", "postgres:17.9"));

	@BeforeAll
	static void start() {
		POSTGRES.start();
	}

	@AfterAll
	static void stop() {
		POSTGRES.stop();
	}

	@Override
	Connection connect() throws Exception {
		return POSTGRES.createConnection("");
	}

	@Override
	boolean realAurora() {
		return false;
	}
}
