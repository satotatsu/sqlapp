/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.aurora.postgres;

import java.sql.Connection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** PostgreSQL compatibility only: this container does not emulate Aurora. */
class AuroraPostgresCompatibilityTest extends AuroraPostgresAssertions {
	@org.junit.jupiter.api.Test
	void verifiesPostgresFamilyDataTypes() throws Exception {
		try (var connection = connect()) {
			com.sqlapp.data.db.dialect.test.DataTypeServerAssertions.verify(connection,
					com.sqlapp.data.db.dialect.DialectResolver.getInstance().getDialect("aurora-postgresql",
							connection.getMetaData().getDatabaseMajorVersion(), 0, null),
					"REAL", "DOUBLE PRECISION", "FLOAT(24)", "FLOAT(53)", "NUMERIC(12,3)", "CHARACTER VARYING(12)",
					"TIME(3) WITHOUT TIME ZONE", "TIMESTAMP(3) WITH TIME ZONE", "INTEGER[]", "DOUBLE PRECISION[]",
					"REAL[]");
		}
	}

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

	@org.junit.jupiter.api.Test
	void preservesSequenceAndAdvancedIndexMetadata() throws Exception {
		try (var c = connect()) {
			com.sqlapp.data.db.dialect.test.postgres.PostgresMetadataRegressionAssertions.verify(c,
					com.sqlapp.data.db.dialect.DialectResolver.getInstance().getDialect("aurora-postgresql",
							c.getMetaData().getDatabaseMajorVersion(), 0, null));
		}
	}
}
