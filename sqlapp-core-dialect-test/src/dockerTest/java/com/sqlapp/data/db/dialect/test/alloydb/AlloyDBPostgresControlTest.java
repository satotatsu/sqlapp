/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.alloydb;

import java.sql.Connection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Ordinary PostgreSQL control: resolver must not identify it as AlloyDB. */
class AlloyDBPostgresControlTest extends AlloyDBAssertions {
	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.9");

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
	boolean alloyDbTarget() {
		return false;
	}

	@org.junit.jupiter.api.Test
	void customPlaceholderDoesNotSpoofRegisteredAlloyFlag() throws Exception {
		try (var connection = connect(); var statement = connection.createStatement()) {
			connection.setAutoCommit(false);
			statement.execute("SET LOCAL google_columnar_engine.enabled = 'on'");
			org.junit.jupiter.api.Assertions.assertFalse(com.sqlapp.data.db.dialect.DialectResolver.getInstance()
					.getDialect(connection) instanceof com.sqlapp.data.db.dialect.alloydb.AlloyDB);
			connection.rollback();
		}
	}


    @org.junit.jupiter.api.Test
    void preservesSequenceAndAdvancedIndexMetadata() throws Exception {
        try (var c = connect()) {
            com.sqlapp.data.db.dialect.test.postgres.PostgresMetadataRegressionAssertions.verify(c, com.sqlapp.data.db.dialect.DialectResolver.getInstance().getDialect(c));
        }
    }
}
